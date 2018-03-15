/*
 * Copyright (c) 2018 Yrom Wang
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.yrom.tools;

import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.function.Function;

import static com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES;

/**
 * Strip annotations which are useless in runtime.
 *
 * e.g. @kotlin.Metadata, @kotlin.jvm.JvmStatic, @org.jetbrains.annotations.NotNull, etc.
 *
 * <br/>
 *
 * NOTE: It's a experiment feature may cause some unexpected issues
 *
 * @author yrom
 */
class StripAnnotationsTransform extends Transform {
    private final ShrinkerExtension config;

    StripAnnotationsTransform(ShrinkerExtension config) {
        this.config = config;
    }

    @Override
    public String getName() {
        return "stripAnnotations";
    }

    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return ImmutableSet.of(CLASSES);
    }

    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        if (!config.stripAnnotations) // empty scope
            return ImmutableSet.of();
        // full
        return Sets.immutableEnumSet(
                QualifiedContent.Scope.PROJECT,
                QualifiedContent.Scope.SUB_PROJECTS,
                QualifiedContent.Scope.EXTERNAL_LIBRARIES);
    }

    @Override
    public Set<? super QualifiedContent.Scope> getReferencedScopes() {
        if (config.stripAnnotations) // empty
            return ImmutableSet.of();
        return Sets.immutableEnumSet(QualifiedContent.Scope.PROJECT);
    }

    @Override
    public boolean isIncremental() {
        return false;
    }


    @Override
    public void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        if (!config.stripAnnotations) {
            ShrinkerPlugin.logger.lifecycle("skip stripAnnotations transform!");
            return;
        }
        if (transformInvocation.isIncremental()) {
            throw new UnsupportedOperationException("Unsupported incremental build!");
        }
        long start = System.currentTimeMillis();
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();

        Collection<TransformInput> inputs = transformInvocation.getInputs();
        if (config.stripAnnotations) {
            Function<QualifiedContent, Path> call = input -> {
                Format format;
                if (input instanceof DirectoryInput) {
                    format = Format.DIRECTORY;
                } else if (input instanceof JarInput) {
                    format = Format.JAR;
                } else {
                    throw new UnsupportedOperationException("Unknown format readAll input " + input);
                }
                File f = outputProvider.getContentLocation(input.getName(), input.getContentTypes(),
                        input.getScopes(), format);
                if (!f.getParentFile().exists()) f.getParentFile().mkdirs();
                return f.toPath();
            };
            new QualifiedContentProcessor(inputs, new StripAnnotationsClassTransform(), call).proceed();
            return;
        }
        // just copy them...
        for (TransformInput input : inputs) {
            input.getDirectoryInputs().forEach(dir -> {
                File destFolder = outputProvider.getContentLocation(dir.getName(),
                        dir.getContentTypes(), dir.getScopes(), Format.DIRECTORY);
                try {
                    FileUtils.copyDirectory(dir.getFile(), destFolder);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            input.getJarInputs().parallelStream().forEach(jarInput -> {
                File dest = outputProvider.getContentLocation(jarInput.getName(),
                        jarInput.getContentTypes(), jarInput.getScopes(), Format.JAR);
                if (dest.exists()) {
                    throw new RuntimeException("Jar file " + jarInput.getName() + " already exists!" +
                            " src: " + jarInput.getFile().getPath() + ", dest: " + dest.getPath());
                }
                try {
                    FileUtils.copyFile(jarInput.getFile(), dest);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        ShrinkerPlugin.logger.info("{} copy files {} ms", transformInvocation.getContext().getPath(), System.currentTimeMillis() - start);
    }
}
