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

import groovy.transform.PackageScope
import org.objectweb.asm.*

/**
 * @author yrom.
 */
@PackageScope
class RClassVisitor extends ClassVisitor {
    String classname
    Object rSymbols
    RClassVisitor(ClassWriter cv, Object rSymbols) {
        super(Opcodes.ASM5, cv)
        this.rSymbols = rSymbols
    }

    @Override
    void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        classname = name
        ShrinkerPlugin.logger.info "processing class $name"
        super.visit(version, access, name, signature, superName, interfaces)
    }

    @Override
    FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        if (access == 0x19 /*ACC_PUBLIC | ACC_STATIC | ACC_FINAL*/
                && classname.endsWith('R$styleable') && value != null) {
            ShrinkerPlugin.logger.debug "remove field $name $signature"
            return null
        }
        return cv.visitField(access, name, desc, signature, value)
    }

    @Override
    void visitInnerClass(String name, String outerName, String innerName, int access) {
        if (access == 0x19 /*ACC_PUBLIC | ACC_STATIC | ACC_FINAL*/
                && !name.startsWith('android/')
                && name ==~ /(\w+\/)+R\$[a-z]+/) {
            ShrinkerPlugin.logger.debug "remove innerclass attribute $name"
            return
        }
        cv.visitInnerClass(name, outerName, innerName, access)
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc,
                                     String signature, String[] exceptions) {
        return new MethodVisitor(Opcodes.ASM5,
                super.visitMethod(access, name, desc, signature, exceptions)) {

            @Override
            void visitFieldInsn(int opcode, String owner, String fieldName,
                                String fieldDesc) {
                if (opcode != Opcodes.GETSTATIC || owner.startsWith('java/lang/')) {
                    // skip!
                    mv.visitFieldInsn(opcode, owner, fieldName, fieldDesc)
                    return
                }
                def typeName = owner.substring(owner.lastIndexOf('/') + 1)
                def key = typeName + '.' + fieldName
                if (rSymbols.containsKey(key)) {
                    //|| typeName ==~ /R\$(?!styleable)[a-z]+/
                    Object value = rSymbols.get(key)
                    if (!(value instanceof Integer))
                        throw new UnsupportedOperationException()

                    ShrinkerPlugin.logger.debug "repace $owner.$fieldName to 0x${Integer.toHexString(value)}"
                    pushInt(this.mv, value)
                } else {
                    this.mv.visitFieldInsn(opcode, owner, fieldName, fieldDesc)
                }
            }
        }
    }

    static void pushInt(MethodVisitor mv, Integer i) {
        if (0 <= i && i <= 5) {
            mv.visitInsn(Opcodes.ICONST_0 + i) //  ICONST_0 ~ ICONST_5
        } else {
            mv.visitLdcInsn(i)
        }
    }
}