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

import com.android.build.api.transform.*
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets
import groovy.transform.PackageScope
import org.apache.commons.io.FileUtils

import static com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES
/**
 * @author yrom.
 */
@PackageScope
class InlineRTransform extends Transform {
    ShrinkerExtension config

    InlineRTransform(ShrinkerExtension config) {
        this.config = config
    }

    @Override
    String getName() { "inlineR" }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() { ImmutableSet.of(CLASSES) }

    @Override
    Set<? super QualifiedContent.Scope> getScopes() {
        if (!config.inlineR) // empty scope
            return ImmutableSet.of()
        // full
        return Sets.immutableEnumSet(
                QualifiedContent.Scope.PROJECT,
                QualifiedContent.Scope.PROJECT_LOCAL_DEPS,
                QualifiedContent.Scope.SUB_PROJECTS,
                QualifiedContent.Scope.SUB_PROJECTS_LOCAL_DEPS,
                QualifiedContent.Scope.EXTERNAL_LIBRARIES)
    }

    @Override
    Set<? super QualifiedContent.Scope> getReferencedScopes() {
        if (config.inlineR) // empty
            return ImmutableSet.of();
        return Sets.immutableEnumSet(QualifiedContent.Scope.PROJECT)
    }

    @Override
    boolean isIncremental() { false }


    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        if (!config.inlineR) {
            ShrinkerPlugin.logger.lifecycle 'skip inlineR transform!'
            return
        }
        if (transformInvocation.incremental) {
            throw new UnsupportedOperationException('Unsupported incremental build!')
        }
        def start = System.currentTimeMillis()
        TransformOutputProvider outputProvider = transformInvocation.outputProvider
        // ${buildType}/folders/${types-name}/${scopes-name}/test
        def file = outputProvider.getContentLocation('test', this.inputTypes, this.scopes, Format.DIRECTORY)
        def buildType = file.parentFile.parentFile.parentFile.parentFile.name
        outputProvider.deleteAll()
        Collection<TransformInput> inputs = transformInvocation.inputs
        if (config.inlineR && buildType != 'debug') {
            def rSymbols = new RSymbols().from(inputs)
            if (!rSymbols.isEmpty()) {
                InlineRProcessor.proceed(inputs,
                        { QualifiedContent input ->
                            def format
                            if (input instanceof DirectoryInput) format = Format.DIRECTORY
                            else if (input instanceof JarInput) format = Format.JAR
                            else throw new UnsupportedOperationException("Unknown format of input " + input)
                            def f = outputProvider.getContentLocation(input.name, input.contentTypes,
                                    input.scopes, format)
                            if (!f.parentFile.exists()) f.parentFile.mkdirs()
                            return f.toPath()
                        },
                        new ClassTransform(rSymbols).transform)
                ShrinkerPlugin.logger.lifecycle "${transformInvocation.context.path} consume ${System.currentTimeMillis() - start}ms"
                return
            }
        }
        // just copy them...
        for (TransformInput input in inputs) {
            input.directoryInputs.each { DirectoryInput dir ->
                File destFolder = outputProvider.getContentLocation(dir.name,
                        dir.contentTypes, dir.scopes, Format.DIRECTORY)
                FileUtils.copyDirectory dir.file, destFolder
            }
            input.jarInputs.each { JarInput jarInput ->
                File dest = outputProvider.getContentLocation(jarInput.name,
                        jarInput.contentTypes, jarInput.scopes, Format.JAR)
                if (dest.exists()) {
                    throw new TransformException("Jar file $jarInput.name already exists!" +
                            " src: $jarInput.file.path, dest: $dest.path")
                }
                FileUtils.copyFile jarInput.file, dest
            }
        }
        ShrinkerPlugin.logger.info "${transformInvocation.context.path} copy files ${System.currentTimeMillis() - start} ms"
    }

}