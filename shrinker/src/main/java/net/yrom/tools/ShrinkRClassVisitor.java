/*
 * Copyright (c) 2017 Yrom Wang
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

import org.gradle.api.logging.LogLevel;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static net.yrom.tools.ShrinkerPlugin.logger;

/**
 * @author yrom
 * @version 2017/11/29
 */
class ShrinkRClassVisitor extends ClassVisitor {

    private String classname;
    private final RSymbols rSymbols;

    /**
     * @return true if name matches pattern like {@code .+/R$.+}
     */
    static boolean isRClass(String className) {
        int $ = className.lastIndexOf('$');
        int slash = className.lastIndexOf('/', $);
        return $ > slash && $ < className.length() && (className.charAt(slash + 1) | className.charAt($ - 1)) == 'R';
    }

    ShrinkRClassVisitor(ClassWriter cv, RSymbols rSymbols) {
        super(Opcodes.ASM5, cv);
        this.rSymbols = rSymbols;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        classname = name;
        logger.debug("processing class " + name);
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        return cv.visitField(access, name, desc, signature, value);
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        if (access == 0x19 /*ACC_PUBLIC | ACC_STATIC | ACC_FINAL*/
                && isRClass(name)) {
            logger.debug("remove visit inner class {} in {}", name, classname);
            return;
        }
        cv.visitInnerClass(name, outerName, innerName, access);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc,
                                     String signature, String[] exceptions) {
        return new MethodVisitor(Opcodes.ASM5,
                super.visitMethod(access, name, desc, signature, exceptions)) {

            @Override
            public void visitFieldInsn(int opcode, String owner, String fieldName,
                                       String fieldDesc) {
                if (opcode != Opcodes.GETSTATIC || owner.startsWith("java/lang/")) {
                    // skip!
                    this.mv.visitFieldInsn(opcode, owner, fieldName, fieldDesc);
                    return;
                }
                String typeName = owner.substring(owner.lastIndexOf('/') + 1);
                String key = typeName + '.' + fieldName;
                if (rSymbols.containsKey(key)) {
                    Integer value = rSymbols.get(key);
                    if (value == null)
                        throw new UnsupportedOperationException("value of " + key + " is null!");
                    if (logger.isEnabled(LogLevel.DEBUG)) {
                        logger.debug("replace {}.{} to 0x{}", owner, fieldName, Integer.toHexString(value));
                    }
                    pushInt(this.mv, value);
                } else if (owner.endsWith("/R$styleable")) { // replace all */R$styleable ref!
                    this.mv.visitFieldInsn(opcode, RSymbols.R_STYLEABLES_CLASS_NAME, fieldName, fieldDesc);
                } else {
                    this.mv.visitFieldInsn(opcode, owner, fieldName, fieldDesc);
                }
            }
        };
    }

    private static void pushInt(MethodVisitor mv, int i) {
        if (0 <= i && i <= 5) {
            mv.visitInsn(Opcodes.ICONST_0 + i); //  ICONST_0 ~ ICONST_5
        } else if (i <= Byte.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.BIPUSH, i);
        } else if (i <= Short.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.SIPUSH, i);
        } else {
            mv.visitLdcInsn(i);
        }
    }
}
