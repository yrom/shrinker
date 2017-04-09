/*
Copyright 2017 Yrom

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package net.yrom.tools

import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.Format
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInput
import com.android.build.api.transform.TransformInvocation
import com.android.build.api.transform.TransformOutputProvider
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets
import groovy.io.FileType
import org.apache.commons.io.FileUtils
import org.objectweb.asm.*
import org.apache.commons.io.IOUtils

import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

import static com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES
import static org.objectweb.asm.ClassReader.*

/**
 * @author yrom.
 */
class InlineRTransform extends Transform {
    ShrinkerExtension config

    InlineRTransform(ShrinkerExtension config) {
        this.config = config
    }

    @Override
    String getName() {
        return "inlineR"
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return ImmutableSet.of(CLASSES)
    }

    @Override
    Set<? super QualifiedContent.Scope> getScopes() {
        // full
        return Sets.immutableEnumSet(
                QualifiedContent.Scope.PROJECT,
                QualifiedContent.Scope.PROJECT_LOCAL_DEPS,
                QualifiedContent.Scope.SUB_PROJECTS,
                QualifiedContent.Scope.SUB_PROJECTS_LOCAL_DEPS,
                QualifiedContent.Scope.EXTERNAL_LIBRARIES)
    }

    @Override
    boolean isIncremental() {
        return false
    }


    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        if (transformInvocation.incremental) {
            throw new UnsupportedOperationException('Unsupported incremental build!')
        }
        TransformOutputProvider outputProvider = transformInvocation.outputProvider
        outputProvider.deleteAll()
        Collection<TransformInput> inputs = transformInvocation.inputs
        if (config.inlineR) {
            def rSymbols = gatherRSymbols(inputs) as Map
            inputs.each { TransformInput input ->
                processAllClasses(input, outputProvider, rSymbols)
            }
        } else {
            // just copy them...
            inputs.each { TransformInput input ->
                input.directoryInputs.each { DirectoryInput dir ->
                    File destFolder = outputProvider.getContentLocation(dir.name,
                            dir.contentTypes, dir.scopes, Format.DIRECTORY);
                    FileUtils.copyDirectory(dir.file, destFolder)
                }
                input.jarInputs.each { JarInput jarInput ->
                    File dest = outputProvider.getContentLocation(jarInput.name,
                            jarInput.contentTypes, jarInput.scopes, Format.JAR)
                    if (dest.exists()) {
                        throw new TransformException("Jar file $jarInput.name already exists!" +
                                " src: $jarInput.file.path, dest: $dest.path")
                    }
                    FileUtils.copyFile(jarInput.file, dest)
                }
            }
        }

    }

    static void processAllClasses(TransformInput input, TransformOutputProvider outputProvider, rSymbols) {
        def classTransform = new ClassTransform(rSymbols)
        for (DirectoryInput dir : input.directoryInputs) {
            File srcFolder = dir.file
            ShrinkerPlugin.logger.info 'Processing folder ' + srcFolder
            File destFolder = outputProvider.getContentLocation(dir.name,
                    dir.contentTypes, dir.scopes, Format.DIRECTORY);
            copy(srcFolder, destFolder, classTransform)
        }
        for (JarInput jarInput : input.jarInputs) {
            ShrinkerPlugin.logger.info 'Processing jar ' + jarInput.file.absolutePath
            File dest = outputProvider.getContentLocation(jarInput.name, jarInput.contentTypes, jarInput.scopes, Format.JAR)
            ZipInputStream zis = null
            JarOutputStream jarOutputStream = null
            try {
                zis = new ZipInputStream(FileUtils.openInputStream(jarInput.file))
                jarOutputStream = new JarOutputStream(FileUtils.openOutputStream(dest))
                ZipEntry entry
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue
                    String name = entry.name
                    if (!name.endsWith('.class')) {
                        continue
                    }
                    JarEntry newEntry
                    if (entry.getMethod() == ZipEntry.STORED) {
                        newEntry = new JarEntry(entry);
                    } else {
                        newEntry = new JarEntry(name);
                    }
                    jarOutputStream.putNextEntry(newEntry);
                    byte[] bytes = classTransform.transform(IOUtils.toByteArray(zis))
                    jarOutputStream.write(bytes)
                    jarOutputStream.closeEntry()
                    zis.closeEntry()
                }
            } catch (Exception e) {
                throw new IOException('Failed to process jar ' + jarInput.file.absolutePath, e)
            } finally {
                IOUtils.closeQuietly(zis)
                IOUtils.closeQuietly(jarOutputStream)
            }
        }
    }

    static void copy(File src, File dest, ClassTransform classFilter) {
        for (File file : src.listFiles()) {
            if (file.isDirectory()) {
                copy(file, new File(dest, file.name), classFilter)
            } else {
                String name = file.name
                // find R.class or R$**.class
                if (name ==~ /R\.class|R\$(?!styleable)[a-z]+\.class/) {
                    ShrinkerPlugin.logger.info ' ignored file ' + file.absolutePath
                } else {
                    byte[] bytes = classFilter.transform(file.bytes)
                    File destFile = new File(dest, name)
                    FileUtils.writeByteArrayToFile(destFile, bytes)
                }
            }
        }
    }

    // parse R$**.class
    static Map<String, Integer> gatherRSymbols(Collection<TransformInput> inputs) {
        def symbols = new HashMap(512)
        for (TransformInput input : inputs) {
            // All aapt generated R classes are in the directory-input stream
            for (DirectoryInput dir : input.directoryInputs) {
                dir.file.eachFileRecurse FileType.FILES, { File it ->
                    if (it.name ==~ /R\$[a-z]+\.class/) {
                        String typeName = it.name - '.class'
                        ClassReader reader = new ClassReader(it.bytes)
                        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM5) {
                            @Override
                            FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                                // read constant value
                                if (value instanceof Integer) {
                                    def key = typeName + '.' + name
                                    symbols.put(key, value)
                                }
                                return null
                            }

                        }
                        reader.accept(visitor, SKIP_CODE | SKIP_DEBUG | SKIP_FRAMES);
                    }
                }
            }
            //TODO: should scan R.class from jar files?
            //if(input.directoryInputs.empty {}
        }

        return ImmutableMap.copyOf(symbols)
    }


}