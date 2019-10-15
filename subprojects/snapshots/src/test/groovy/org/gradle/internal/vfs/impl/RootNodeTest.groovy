/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.vfs.impl

import com.google.common.collect.ImmutableList
import spock.lang.Specification

class RootNodeTest extends Specification {

    def "can add unix style children"() {
        def node = new RootNode()

        def directChild = node
            .replaceDescendant(ImmutableList.of("", "var"), { parent -> new DefaultNode("var", parent) })
        def existingDirectChild = node.getDescendant(ImmutableList.of("", "var"))
        def childPath = "${File.separator}var"
        expect:
        directChild.absolutePath == childPath
        existingDirectChild.absolutePath == childPath
        directChild
            .replaceDescendant(ImmutableList.of("log")) { parent -> new DefaultNode("log", parent) }
            .absolutePath == ["", "var", "log"].join(File.separator)
        existingDirectChild.getDescendant(ImmutableList.of("log")).absolutePath == ["", "var", "log"].join(File.separator)
        node.getDescendant(ImmutableList.of("", "var", "log")).absolutePath == ["", "var", "log"].join(File.separator)
    }

    def "can add Windows style children"() {
        def node = new RootNode()

        def directChild = node
            .replaceDescendant(ImmutableList.of("C:")) { parent -> new DefaultNode("C:", parent) }
        def existingDirectChild = node.getDescendant(ImmutableList.of("C:"))
        expect:
        directChild.absolutePath == "C:"
        existingDirectChild.absolutePath == "C:"
        directChild
            .replaceDescendant(ImmutableList.of("Users")) { parent -> new DefaultNode("Users", parent) }
            .absolutePath == ["C:", "Users"].join(File.separator)
        existingDirectChild.getDescendant(ImmutableList.of("Users")).absolutePath == ["C:", "Users"].join(File.separator)
    }
}
