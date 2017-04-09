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
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter

/**
 * @author yrom.
 */
@PackageScope
class ClassTransform {
    Map<String, Integer> rSymbols

    ClassTransform(Map<String, Integer> rSymbols) {
        this.rSymbols = rSymbols
    }

    byte[] transform(byte[] origin) {
        ClassReader reader = new ClassReader(origin)
        // don't pass reader to the writer.
        // or it will copy 'CONSTANT POOL' that contains no used entries to lead proguard running failed!
        ClassWriter writer = new ClassWriter(0)
        ClassVisitor visitor = new RClassVisitor(writer, rSymbols)
        reader.accept(visitor, 0)
        return writer.toByteArray()
    }
}