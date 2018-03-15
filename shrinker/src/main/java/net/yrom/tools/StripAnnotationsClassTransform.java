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

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.function.Function;

import static org.objectweb.asm.ClassReader.SKIP_DEBUG;
import static org.objectweb.asm.ClassReader.SKIP_FRAMES;

/**
 * @author yrom
 */
class StripAnnotationsClassTransform implements Function<byte[], byte[]> {
    @Override
    public byte[] apply(byte[] origin) {

        ClassReader reader = new ClassReader(origin);
        AnnotationsVisitor precondition = new AnnotationsVisitor();
        reader.accept(precondition, SKIP_DEBUG | SKIP_FRAMES);
        if (!precondition.needStripAnnotations) {
            return origin;
        }

        ClassWriter writer = new ClassWriter(0);
        ClassVisitor visitor = new StripMetadataClassVisitor(writer);
        reader.accept(visitor, 0);
        return writer.toByteArray();

    }

    static class AnnotationsVisitor extends ClassVisitor {
        boolean needStripAnnotations;

        AnnotationsVisitor() {
            super(Opcodes.ASM5);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            if (!needStripAnnotations) {
                needStripAnnotations = isAnnotationUseless(desc);
            }
            return null;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
            if (!needStripAnnotations)
                return new FieldVisitor(Opcodes.ASM5) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        if (!needStripAnnotations) {
                            needStripAnnotations = isAnnotationUseless(desc);
                        }
                        return null;
                    }
                };
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            if (!needStripAnnotations) {
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        if (!needStripAnnotations) {
                            needStripAnnotations = isAnnotationUseless(desc);
                        }
                        return null;
                    }
                };
            }
            return null;
        }
    }

    private static boolean isAnnotationUseless(String desc) {
        return desc.equals("Lkotlin/Metadata;")
                || desc.startsWith("Lkotlin/jvm/")
                || desc.startsWith("Lkotlin/internal/")
                || desc.startsWith("Ljavax/annotation/")
                || desc.endsWith("/NotNull;")
                || desc.endsWith("/Nullable;")
                || desc.endsWith("/VisibleForTesting;")
                || desc.equals("Ljava/lang/Deprecated;")
                || (desc.startsWith("Landroid/support/annotation") && !desc.endsWith("Keep;"))
                ;
    }

    static class StripMetadataClassVisitor extends ClassVisitor {
        private String className;
        private boolean skipStrip;

        StripMetadataClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM5, cv);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            className = name;
            //TODO: skipStrip
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            if (!skipStrip && "Lkotlin/Metadata;".equals(desc)) {
                ShrinkerPlugin.logger.lifecycle("strip class annotation of " + className);
                return null;
            }
            return cv.visitAnnotation(desc, visible);
        }


        @Override
        public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
            FieldVisitor visitor = super.visitField(access, name, desc, signature, value);
            if (!skipStrip)
                return new FieldVisitor(Opcodes.ASM5, visitor) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        if (isAnnotationUseless(desc)) {
                            ShrinkerPlugin.logger.debug("strip field annotation {} of {}", desc, name);
                            return null;
                        }
                        return super.visitAnnotation(desc, true);
                    }
                };
            return visitor;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor methodVisitor = super.visitMethod(access, name, desc, signature, exceptions);
            if (!skipStrip) {
                return new MethodVisitor(Opcodes.ASM5, methodVisitor) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        if (isAnnotationUseless(desc)) {
                            ShrinkerPlugin.logger.debug("strip method annotation {} of {}", desc, name);
                            return null;
                        }
                        return super.visitAnnotation(desc, true);
                    }

                    @Override
                    public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
                        if (isAnnotationUseless(desc)) {
                            ShrinkerPlugin.logger.debug("strip method parameter annotation {} of {}", desc, name);
                            return null;
                        }
                        return super.visitParameterAnnotation(parameter, desc, true);
                    }
                };
            }
            return methodVisitor;
        }
    }
}

