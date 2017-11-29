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

import com.android.build.gradle.AppExtension;
import com.android.build.gradle.AppPlugin;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

/**
 * @author yrom.
 */
public class ShrinkerPlugin implements Plugin<Project> {
    static final Logger logger = Logging.getLogger(ShrinkerPlugin.class);

    @Override
    public void apply(Project project) {
        if (!project.getPlugins().hasPlugin(AppPlugin.class)) {
            throw new UnsupportedOperationException("Plugin 'shrinker' can only apply with 'com.android.application'");
        }
        AppExtension android = project.getExtensions().getByType(AppExtension.class);
        ShrinkerExtension config = project.getExtensions().create("shrinker", ShrinkerExtension.class);
        android.registerTransform(new InlineRTransform(config));
    }
}