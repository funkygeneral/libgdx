/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.badlogic.gdx.setup;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;

import com.badlogic.gdx.setup.DependencyBank.ProjectDependency;
import com.badlogic.gdx.setup.DependencyBank.ProjectType;
import com.badlogic.gdx.setup.DependencyBank.DimensionType;
import com.badlogic.gdx.setup.DependencyBank.ScreenType;
import com.badlogic.gdx.setup.Executor.CharCallback;

/** Command line tool to generate libgdx projects
 * @author badlogic
 * @author Tomski */
public class GdxSetup {
    public static boolean isSdkLocationValid(String sdkLocation) {
        return new File(sdkLocation, "tools").exists() && new File(sdkLocation, "platforms").exists();
    }

    public static boolean isEmptyDirectory(String destination) {
        if (new File(destination).exists()) {
            return new File(destination).list().length == 0;
        } else {
            return true;
        }
    }

    public static boolean isSdkUpToDate(String sdkLocation) {
        File buildTools = new File(sdkLocation, "build-tools");
        if (!buildTools.exists()) {
            JOptionPane.showMessageDialog(null, "You have no build tools!\nUpdate your Android SDK with build tools version: "
                    + DependencyBank.buildToolsVersion);
            return false;
        }

        File apis = new File(sdkLocation, "platforms");
        if (!apis.exists()) {
            JOptionPane.showMessageDialog(null, "You have no Android APIs!\nUpdate your Android SDK with API level: "
                    + DependencyBank.androidAPILevel);
            return false;
        }
        String newestLocalTool = getLatestTools(buildTools);
        int[] localToolVersion = convertTools(newestLocalTool);
        int[] targetToolVersion = convertTools(DependencyBank.buildToolsVersion);
        if (compareVersions(targetToolVersion, localToolVersion)) {
            int value = JOptionPane.showConfirmDialog(null,
                    "You have a more recent version of android build tools than the recommended.\nDo you want to use your more recent version?",
                    "Warning!", JOptionPane.YES_NO_OPTION);
            if (value != 0) {
                JOptionPane.showMessageDialog(null, "Using build tools: " + DependencyBank.buildToolsVersion);
            } else {
                DependencyBank.buildToolsVersion = newestLocalTool;
            }
        } else {
            if (!versionsEqual(localToolVersion, targetToolVersion)) {
                JOptionPane.showMessageDialog(null, "Please update your Android SDK, you need build tools: "
                        + DependencyBank.buildToolsVersion);
                return false;
            }
        }

        int newestLocalApi = getLatestApi(apis);
        if (newestLocalApi > Integer.valueOf(DependencyBank.androidAPILevel)) {
            int value = JOptionPane.showConfirmDialog(null,
                    "You have a more recent Android API than the recommended.\nDo you want to use your more recent version?", "Warning!",
                    JOptionPane.YES_NO_OPTION);
            if (value != 0) {
                JOptionPane.showMessageDialog(null, "Using API level: " + DependencyBank.androidAPILevel);
            } else {
                DependencyBank.androidAPILevel = String.valueOf(newestLocalApi);
            }
        } else {
            if (newestLocalApi != Integer.valueOf(DependencyBank.androidAPILevel)) {
                JOptionPane.showMessageDialog(null, "Please update your Android SDK, you need the Android API: "
                        + DependencyBank.androidAPILevel);
                return false;
            }
        }
        return true;
    }

    private static int getLatestApi(File apis) {
        int apiLevel = 0;
        for (File api : apis.listFiles()) {
            int level = readAPIVersion(api);
            if (level > apiLevel) apiLevel = level;
        }
        return apiLevel;
    }

    private static String getLatestTools(File buildTools) {
        String version = null;
        int[] versionSplit = new int[3];
        int[] testSplit = new int[3];
        for (File toolsVersion : buildTools.listFiles()) {
            if (version == null) {
                version = readBuildToolsVersion(toolsVersion);
                versionSplit = convertTools(version);
                continue;
            }
            testSplit = convertTools(readBuildToolsVersion(toolsVersion));
            if (compareVersions(versionSplit, testSplit)) {
                version = readBuildToolsVersion(toolsVersion);
                versionSplit = convertTools(version);
            }
        }
        if (version != null) {
            return version;
        } else {
            return "0.0.0";
        }
    }

    private static int readAPIVersion(File parentFile) {
        File properties = new File(parentFile, "source.properties");
        FileReader reader;
        BufferedReader buffer;
        try {
            reader = new FileReader(properties);
            buffer = new BufferedReader(reader);

            String line = null;

            while ((line = buffer.readLine()) != null) {
                if (line.contains("AndroidVersion.ApiLevel")) {

                    String versionString = line.split("\\=")[1];
                    int apiLevel = Integer.parseInt(versionString);

                    buffer.close();
                    reader.close();

                    return apiLevel;
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private static String readBuildToolsVersion(File parentFile) {
        File properties = new File(parentFile, "source.properties");
        FileReader reader;
        BufferedReader buffer;
        try {
            reader = new FileReader(properties);
            buffer = new BufferedReader(reader);

            String line = null;

            while ((line = buffer.readLine()) != null) {
                if (line.contains("Pkg.Revision")) {

                    String versionString = line.split("\\=")[1];
                    int count = versionString.split("\\.").length;
                    for (int i = 0; i < 3 - count; i++) {
                        versionString += ".0";
                    }

                    buffer.close();
                    reader.close();

                    return versionString;
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "0.0.0";
    }

    private static boolean versionsEqual(int[] testVersion, int[] targetVersion) {
        for (int i = 0; i < 3; i++) {
            if (testVersion[i] != targetVersion[i]) return false;
        }
        return true;
    }

    private static boolean compareVersions(int[] version, int[] testVersion) {
        if (testVersion[0] > version[0]) {
            return true;
        } else if (testVersion[0] == version[0]) {
            if (testVersion[1] > version[1]) {
                return true;
            } else if (testVersion[1] == version[1]) {
                return testVersion[2] > version[2];
            }
        }
        return false;
    }

    private static int[] convertTools(String toolsVersion) {
        String[] stringSplit = toolsVersion.split("\\.");
        int[] versionSplit = new int[3];
        if (stringSplit.length == 3) {
            try {
                versionSplit[0] = Integer.parseInt(stringSplit[0]);
                versionSplit[1] = Integer.parseInt(stringSplit[1]);
                versionSplit[2] = Integer.parseInt(stringSplit[2]);
                return versionSplit;
            } catch (NumberFormatException nfe) {
                return new int[]{0, 0, 0};
            }
        } else {
            return new int[]{0, 0, 0};
        }
    }

    public void build(ProjectBuilder builder, String outputDir, String appName, String packageName, String mainClass,
                      String sdkLocation, CharCallback callback, List<String> gradleArgs) {
        Project project = new Project();

        String packageDir = packageName.replace('.', '/');
        String sdkPath = sdkLocation.replace('\\', '/');

        if (!isSdkLocationValid(sdkLocation)) {
            System.out.println("Android SDK location '" + sdkLocation + "' doesn't contain an SDK");
        }

        // root dir/gradle files
        project.files.add(new ProjectFile("gitignore", ".gitignore", false));
        project.files.add(new TemporaryProjectFile(builder.settingsFile, "settings.gradle", false));
        project.files.add(new TemporaryProjectFile(builder.buildFile, "build.gradle", true));
        project.files.add(new ProjectFile("gradlew", false));
        project.files.add(new ProjectFile("gradlew.bat", false));
        project.files.add(new ProjectFile("gradle/wrapper/gradle-wrapper.jar", false));
        project.files.add(new ProjectFile("gradle/wrapper/gradle-wrapper.properties", false));
        project.files.add(new ProjectFile("gradle.properties"));

        // core project
        project.files.add(new ProjectFile("core/build.gradle"));
        project.files.add(new ProjectFile("core/src/MainClass", "core/src/" + packageDir + "/" + mainClass + ".java", true));
        if (builder.modules.contains(ProjectType.HTML)) {
            project.files.add(new ProjectFile("core/CoreGdxDefinition", "core/src/" + mainClass + ".gwt.xml", true));
        }

        // desktop project
        if (builder.modules.contains(ProjectType.DESKTOP)) {
            project.files.add(new ProjectFile("desktop/build.gradle"));
            project.files.add(new ProjectFile("desktop/src/DesktopLauncher", "desktop/src/" + packageDir + "/desktop/DesktopLauncher.java", true));
        }

        // Assets
        String assetPath = builder.modules.contains(ProjectType.ANDROID) ? "android/assets" : "core/assets";

        // Fonts
        // 320dp
        project.files.add(new ProjectFile("android/assets/font/320dp/big_font.fnt", assetPath + "/font/320dp/big_font.fnt", false));
        project.files.add(new ProjectFile("android/assets/font/320dp/description_font.fnt", assetPath + "/font/320dp/description_font.fnt", false));
        project.files.add(new ProjectFile("android/assets/font/320dp/small_font.fnt", assetPath + "/font/320dp/small_font.fnt", false));
        project.files.add(new ProjectFile("android/assets/font/320dp/font8.png", assetPath + "/font/320dp/font8.png", false));
        project.files.add(new ProjectFile("android/assets/font/320dp/font16.png", assetPath + "/font/320dp/font16.png", false));
        project.files.add(new ProjectFile("android/assets/font/320dp/font32.png", assetPath + "/font/320dp/font32.png", false));

        // 520dp
        project.files.add(new ProjectFile("android/assets/font/520dp/big_font.fnt", assetPath + "/font/520dp/big_font.fnt", false));
        project.files.add(new ProjectFile("android/assets/font/520dp/description_font.fnt", assetPath + "/font/520dp/description_font.fnt", false));
        project.files.add(new ProjectFile("android/assets/font/520dp/small_font.fnt", assetPath + "/font/520dp/small_font.fnt", false));
        project.files.add(new ProjectFile("android/assets/font/520dp/font12.png", assetPath + "/font/520dp/font12.png", false));
        project.files.add(new ProjectFile("android/assets/font/520dp/font24.png", assetPath + "/font/520dp/font24.png", false));
        project.files.add(new ProjectFile("android/assets/font/520dp/font48.png", assetPath + "/font/520dp/font48.png", false));

        // 720dp
        project.files.add(new ProjectFile("android/assets/font/720dp/big_font.fnt", assetPath + "/font/720dp/big_font.fnt", false));
        project.files.add(new ProjectFile("android/assets/font/720dp/description_font.fnt", assetPath + "/font/720dp/description_font.fnt", false));
        project.files.add(new ProjectFile("android/assets/font/720dp/small_font.fnt", assetPath + "/font/720dp/small_font.fnt", false));
        project.files.add(new ProjectFile("android/assets/font/720dp/font16.png", assetPath + "/font/720dp/font16.png", false));
        project.files.add(new ProjectFile("android/assets/font/720dp/font32.png", assetPath + "/font/720dp/font32.png", false));
        project.files.add(new ProjectFile("android/assets/font/720dp/font64.png", assetPath + "/font/720dp/font64.png", false));

        // 1080dp
        project.files.add(new ProjectFile("android/assets/font/1080dp/big_font.fnt", assetPath + "/font/1080dp/big_font.fnt", false));
        project.files.add(new ProjectFile("android/assets/font/1080dp/description_font.fnt", assetPath + "/font/1080dp/description_font.fnt", false));
        project.files.add(new ProjectFile("android/assets/font/1080dp/small_font.fnt", assetPath + "/font/1080dp/small_font.fnt", false));
        project.files.add(new ProjectFile("android/assets/font/1080dp/font32.png", assetPath + "/font/1080dp/font32.png", false));
        project.files.add(new ProjectFile("android/assets/font/1080dp/font64.png", assetPath + "/font/1080dp/font64.png", false));
        project.files.add(new ProjectFile("android/assets/font/1080dp/font128.png", assetPath + "/font/1080dp/font128.png", false));

        // Models
        project.files.add(new ProjectFile("android/assets/models/fur_texture.png", assetPath + "/models/fur_texture.png", false));
        project.files.add(new ProjectFile("android/assets/models/game_model.g3dj", assetPath + "/models/game_model.g3dj", false));
        project.files.add(new ProjectFile("android/assets/models/wood_texture.jpg", assetPath + "/models/wood_texture.jpg", false));

        // Shaders
        project.files.add(new ProjectFile("android/assets/shaders/gaussian/gaussian.fragment.glsl", assetPath + "/shaders/gaussian/gaussian.fragment.glsl", false));
        project.files.add(new ProjectFile("android/assets/shaders/gaussian/gaussian.vertex.glsl", assetPath + "/shaders/gaussian/gaussian.vertex.glsl", false));
        project.files.add(new ProjectFile("android/assets/shaders/sample/sample.fragment.glsl", assetPath + "/shaders/sample/sample.fragment.glsl", false));
        project.files.add(new ProjectFile("android/assets/shaders/sample/sample.vertex.glsl", assetPath + "/shaders/sample/sample.vertex.glsl", false));
        project.files.add(new ProjectFile("android/assets/shaders/test/test.fragment.glsl", assetPath + "/shaders/test/test.fragment.glsl", false));
        project.files.add(new ProjectFile("android/assets/shaders/test/test.vertex.glsl", assetPath + "/shaders/test/test.vertex.glsl", false));
        project.files.add(new ProjectFile("android/assets/shaders/toonify/toonify.fragment.glsl", assetPath + "/shaders/toonify/toonify.fragment.glsl", false));
        project.files.add(new ProjectFile("android/assets/shaders/toonify/toonify.vertex.glsl", assetPath + "/shaders/toonify/toonify.vertex.glsl", false));

        // Sounds
        project.files.add(new ProjectFile("android/assets/sounds/click.mp3", assetPath + "/sounds/click.mp3", false));
        project.files.add(new ProjectFile("android/assets/sounds/roq_sinf.mp3", assetPath + "/sounds/roq_sinf.mp3", false));

        String dimensionPackage = "twodimensional";
        if (builder.dimensions.contains(DimensionType.TWO_DIMENSIONAL)) {
            dimensionPackage = "twodimensional";
        } else if (builder.dimensions.contains(DimensionType.THREE_DIMENSIONAL)) {
            dimensionPackage = "threedimensional";
        }
        // Textures
        // 320dp
        project.files.add(new ProjectFile("android/assets/textures/" + dimensionPackage + "/320dp/loading.txt", assetPath + "/textures/320dp/loading.txt", false));
        project.files.add(new ProjectFile("android/assets/textures/" + dimensionPackage + "/320dp/loading.png", assetPath + "/textures/320dp/loading.png", false));
        project.files.add(new ProjectFile("android/assets/textures/" + dimensionPackage + "/320dp/main.txt", assetPath + "/textures/320dp/main.txt", false));
        project.files.add(new ProjectFile("android/assets/textures/" + dimensionPackage + "/320dp/main320.png", assetPath + "/textures/320dp/main320.png", false));

        // 520dp
        project.files.add(new ProjectFile("android/assets/textures/" + dimensionPackage + "/520dp/loading.txt", assetPath + "/textures/520dp/loading.txt", false));
        project.files.add(new ProjectFile("android/assets/textures/" + dimensionPackage + "/520dp/loading.png", assetPath + "/textures/520dp/loading.png", false));
        project.files.add(new ProjectFile("android/assets/textures/" + dimensionPackage + "/520dp/main.txt", assetPath + "/textures/520dp/main.txt", false));
        project.files.add(new ProjectFile("android/assets/textures/" + dimensionPackage + "/520dp/main520.png", assetPath + "/textures/520dp/main520.png", false));

        // 720dp
        project.files.add(new ProjectFile("android/assets/textures/" + dimensionPackage + "/720dp/loading.txt", assetPath + "/textures/720dp/loading.txt", false));
        project.files.add(new ProjectFile("android/assets/textures/" + dimensionPackage + "/720dp/loading.png", assetPath + "/textures/720dp/loading.png", false));
        project.files.add(new ProjectFile("android/assets/textures/" + dimensionPackage + "/720dp/main.txt", assetPath + "/textures/720dp/main.txt", false));
        project.files.add(new ProjectFile("android/assets/textures/" + dimensionPackage + "/720dp/main720.png", assetPath + "/textures/720dp/main720.png", false));

        // 1080dp
        project.files.add(new ProjectFile("android/assets/textures/" + dimensionPackage + "/1080dp/loading.txt", assetPath + "/textures/1080dp/loading.txt", false));
        project.files.add(new ProjectFile("android/assets/textures/" + dimensionPackage + "/1080dp/loading.png", assetPath + "/textures/1080dp/loading.png", false));
        project.files.add(new ProjectFile("android/assets/textures/" + dimensionPackage + "/1080dp/main.txt", assetPath + "/textures/1080dp/main.txt", false));
        project.files.add(new ProjectFile("android/assets/textures/" + dimensionPackage + "/1080dp/main1080.png", assetPath + "/textures/1080dp/main1080.png", false));

        // android project
        if (builder.modules.contains(ProjectType.ANDROID)) {
            project.files.add(new ProjectFile("android/res/values/strings.xml"));
            project.files.add(new ProjectFile("android/res/values/styles.xml", false));
            project.files.add(new ProjectFile("android/res/drawable-hdpi/ic_launcher.png", false));
            project.files.add(new ProjectFile("android/res/drawable-mdpi/ic_launcher.png", false));
            project.files.add(new ProjectFile("android/res/drawable-xhdpi/ic_launcher.png", false));
            project.files.add(new ProjectFile("android/res/drawable-xxhdpi/ic_launcher.png", false));
            project.files.add(new ProjectFile("android/src/AndroidLauncher", "android/src/" + packageDir + "/AndroidLauncher.java", true));
            project.files.add(new ProjectFile("android/AndroidManifest.xml"));
            project.files.add(new ProjectFile("android/build.gradle", true));
            project.files.add(new ProjectFile("android/ic_launcher-web.png", false));
            project.files.add(new ProjectFile("android/proguard-project.txt", false));
            project.files.add(new ProjectFile("android/project.properties", false));
            project.files.add(new ProjectFile("local.properties", true));
        }

        // html project
        if (builder.modules.contains(ProjectType.HTML)) {
            project.files.add(new ProjectFile("html/build.gradle"));
            project.files.add(new ProjectFile("html/src/HtmlLauncher", "html/src/" + packageDir + "/client/HtmlLauncher.java", true));
            project.files.add(new ProjectFile("html/GdxDefinition", "html/src/" + packageDir + "/GdxDefinition.gwt.xml", true));
            project.files.add(new ProjectFile("html/GdxDefinitionSuperdev", "html/src/" + packageDir + "/GdxDefinitionSuperdev.gwt.xml", true));
            project.files.add(new ProjectFile("html/war/index", "html/webapp/index.html", true));
            project.files.add(new ProjectFile("html/war/styles.css", "html/webapp/styles.css", false));
            project.files.add(new ProjectFile("html/war/refresh.png", "html/webapp/refresh.png", false));
            project.files.add(new ProjectFile("html/war/soundmanager2-jsmin.js", "html/webapp/soundmanager2-jsmin.js", false));
            project.files.add(new ProjectFile("html/war/soundmanager2-setup.js", "html/webapp/soundmanager2-setup.js", false));
            project.files.add(new ProjectFile("html/war/WEB-INF/web.xml", "html/webapp/WEB-INF/web.xml", true));
        }

        // ios robovm
        if (builder.modules.contains(ProjectType.IOS)) {
            project.files.add(new ProjectFile("ios/src/IOSLauncher", "ios/src/" + packageDir + "/IOSLauncher.java", true));
            project.files.add(new ProjectFile("ios/data/Default.png", false));
            project.files.add(new ProjectFile("ios/data/Default@2x.png", false));
            project.files.add(new ProjectFile("ios/data/Default@2x~ipad.png", false));
            project.files.add(new ProjectFile("ios/data/Default-568h@2x.png", false));
            project.files.add(new ProjectFile("ios/data/Default~ipad.png", false));
            project.files.add(new ProjectFile("ios/data/Default-375w-667h@2x.png", false));
            project.files.add(new ProjectFile("ios/data/Default-414w-736h@3x.png", false));
            project.files.add(new ProjectFile("ios/data/Icon.png", false));
            project.files.add(new ProjectFile("ios/data/Icon@2x.png", false));
            project.files.add(new ProjectFile("ios/data/Icon-72.png", false));
            project.files.add(new ProjectFile("ios/data/Icon-72@2x.png", false));
            project.files.add(new ProjectFile("ios/build.gradle", true));
            project.files.add(new ProjectFile("ios/Info.plist.xml", false));
            project.files.add(new ProjectFile("ios/robovm.properties"));
            project.files.add(new ProjectFile("ios/robovm.xml", true));
        }

        if (builder.dimensions.contains(DimensionType.TWO_DIMENSIONAL)) {
            dimensionPackage = "twodimensional";
            // twodimensional project
            project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/Assets", "core/src/" + packageDir + "/Assets.java", true));
            project.files.add(new ProjectFile("core/src/Settings", "core/src/" + packageDir + "/Settings.java", true));
            project.files.add(new ProjectFile("core/src/IActivityRequestHandler", "core/src/" + packageDir + "/IActivityRequestHandler.java", true));

            // extendables
            project.files.add(new ProjectFile("core/src/extendables/MainClassMenu", "core/src/" + packageDir + "/extendables/" + mainClass + "Menu.java", true));
            project.files.add(new ProjectFile("core/src/extendables/MainClassMenuRenderer", "core/src/" + packageDir + "/extendables/" + mainClass + "MenuRenderer.java", true));
            project.files.add(new ProjectFile("core/src/extendables/MainClassScreen", "core/src/" + packageDir + "/extendables/" + mainClass + "Screen.java", true));
            project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/extendables/MainClassWorld", "core/src/" + packageDir + "/extendables/" + mainClass + "World.java", true));
            project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/extendables/MainClassWorldRenderer", "core/src/" + packageDir + "/extendables/" + mainClass + "WorldRenderer.java", true));

            // objects
            // sprites
            project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/objects/DynamicGameObject", "core/src/" + packageDir + "/objects/DynamicGameObject.java", true));
            project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/objects/GameObject", "core/src/" + packageDir + "/objects/GameObject.java", true));
            project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/objects/BurnBar", "core/src/" + packageDir + "/objects/BurnBar.java", true));
            project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/objects/DeveloperLogo", "core/src/" + packageDir + "/objects/DeveloperLogo.java", true));
            project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/objects/Girl", "core/src/" + packageDir + "/objects/Girl.java", true));
            project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/objects/Logo", "core/src/" + packageDir + "/objects/Logo.java", true));
            project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/objects/Obstacle", "core/src/" + packageDir + "/objects/Obstacle.java", true));
            project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/objects/PlayButton", "core/src/" + packageDir + "/objects/PlayButton.java", true));
            project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/objects/PauseButton", "core/src/" + packageDir + "/objects/PauseButton.java", true));
            project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/objects/Rectangle", "core/src/" + packageDir + "/objects/Rectangle.java", true));
            project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/objects/Score", "core/src/" + packageDir + "/objects/Score.java", true));
            project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/objects/Sun", "core/src/" + packageDir + "/objects/Sun.java", true));
            project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/objects/SunScreen", "core/src/" + packageDir + "/objects/SunScreen.java", true));

            if (builder.screens.contains(ScreenType.LOADING)) {
                project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/screens/loading/LoadingMenu", "core/src/" + packageDir + "/screens/loading/LoadingMenu.java", true));
                project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/screens/loading/LoadingMenuRenderer", "core/src/" + packageDir + "/screens/loading/LoadingMenuRenderer.java", true));
                project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/screens/loading/LoadingScreen", "core/src/" + packageDir + "/screens/loading/LoadingScreen.java", true));
            }
            if (builder.screens.contains(ScreenType.MAIN)) {
                project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/screens/main/MainMenu", "core/src/" + packageDir + "/screens/main/MainMenu.java", true));
                project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/screens/main/MainMenuRenderer", "core/src/" + packageDir + "/screens/main/MainMenuRenderer.java", true));
                project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/screens/main/MainWorld", "core/src/" + packageDir + "/screens/main/MainWorld.java", true));
                project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/screens/main/MainScreen", "core/src/" + packageDir + "/screens/main/MainScreen.java", true));
                project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/screens/main/MainWorldRenderer", "core/src/" + packageDir + "/screens/main/MainWorldRenderer.java", true));
            }
        } else if(builder.dimensions.contains(DimensionType.THREE_DIMENSIONAL)) {
            dimensionPackage = "threedimensional";
            // threedimensional project
            project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/Assets", "core/src/" + packageDir + "/Assets.java", true));
            project.files.add(new ProjectFile("core/src/Settings", "core/src/" + packageDir + "/Settings.java", true));
            project.files.add(new ProjectFile("core/src/IActivityRequestHandler", "core/src/" + packageDir + "/IActivityRequestHandler.java", true));

            // extendables
            project.files.add(new ProjectFile("core/src/extendables/MainClassMenu", "core/src/" + packageDir + "/extendables/" + mainClass + "Menu.java", true));
            project.files.add(new ProjectFile("core/src/extendables/MainClassMenuRenderer", "core/src/" + packageDir + "/extendables/" + mainClass + "MenuRenderer.java", true));
            project.files.add(new ProjectFile("core/src/extendables/MainClassScreen", "core/src/" + packageDir + "/extendables/" + mainClass + "Screen.java", true));
            project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/extendables/MainClassWorld", "core/src/" + packageDir + "/extendables/" + mainClass + "World.java", true));
            project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/extendables/MainClassWorldRenderer", "core/src/" + packageDir + "/extendables/" + mainClass + "WorldRenderer.java", true));

            // objects
            // models
            project.files.add(new ProjectFile("core/src/objects/models/GameObject", "core/src/" + packageDir + "/objects/models/GameObject.java", true));
            project.files.add(new ProjectFile("core/src/objects/models/MainClassObject", "core/src/" + packageDir + "/objects/models/" + mainClass + "Object.java", true));
            project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/objects/models/Character", "core/src/" + packageDir + "/objects/models/Character.java", true));
            project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/objects/models/Floor", "core/src/" + packageDir + "/objects/models/Floor.java", true));

            // sprites
            project.files.add(new ProjectFile("core/src/objects/sprites/DynamicGameObject", "core/src/" + packageDir + "/objects/sprites/DynamicGameObject.java", true));
            project.files.add(new ProjectFile("core/src/objects/sprites/GameObject", "core/src/" + packageDir + "/objects/sprites/GameObject.java", true));
            project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/objects/sprites/Button", "core/src/" + packageDir + "/objects/sprites/Button.java", true));
            project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/objects/sprites/DeveloperLogo", "core/src/" + packageDir + "/objects/sprites/DeveloperLogo.java", true));
            project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/objects/sprites/Logo", "core/src/" + packageDir + "/objects/sprites/Logo.java", true));
            project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/objects/sprites/PlayButton", "core/src/" + packageDir + "/objects/sprites/PlayButton.java", true));

            // shaders
            project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/shaders/GaussianShader", "core/src/" + packageDir + "/shaders/GaussianShader.java", true));
            project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/shaders/SampleShader", "core/src/" + packageDir + "/shaders/SampleShader.java", true));
            project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/shaders/TestShader", "core/src/" + packageDir + "/shaders/TestShader.java", true));
            project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/shaders/ToonifyShader", "core/src/" + packageDir + "/shaders/ToonifyShader.java", true));

            if (builder.screens.contains(ScreenType.LOADING)) {
                project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/screens/loading/LoadingMenu", "core/src/" + packageDir + "/screens/loading/LoadingMenu.java", true));
                project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/screens/loading/LoadingMenuRenderer", "core/src/" + packageDir + "/screens/loading/LoadingMenuRenderer.java", true));
                project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/screens/loading/LoadingScreen", "core/src/" + packageDir + "/screens/loading/LoadingScreen.java", true));
            }
            if (builder.screens.contains(ScreenType.MAIN)) {
                project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/screens/main/MainMenu", "core/src/" + packageDir + "/screens/main/MainMenu.java", true));
                project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/screens/main/MainMenuRenderer", "core/src/" + packageDir + "/screens/main/MainMenuRenderer.java", true));
                project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/screens/main/MainWorld", "core/src/" + packageDir + "/screens/main/MainWorld.java", true));
                project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/screens/main/MainScreen", "core/src/" + packageDir + "/screens/main/MainScreen.java", true));
                project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/screens/main/MainWorldRenderer", "core/src/" + packageDir + "/screens/main/MainWorldRenderer.java", true));
            }
            if (builder.screens.contains(ScreenType.GAME)) {
                project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/screens/game/GameMenu", "core/src/" + packageDir + "/screens/game/GameMenu.java", true));
                project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/screens/game/GameMenuRenderer", "core/src/" + packageDir + "/screens/game/GameMenuRenderer.java", true));
                project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/screens/game/GameScreen", "core/src/" + packageDir + "/screens/game/GameScreen.java", true));
                project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/screens/game/GameWorld", "core/src/" + packageDir + "/screens/game/GameWorld.java", true));
                project.files.add(new ProjectFile("core/src/" + dimensionPackage + "/screens/game/GameWorldRenderer", "core/src/" + packageDir + "/screens/game/GameWorldRenderer.java", true));
            }
        }


        Map<String, String> values = new HashMap<String, String>();
        values.put("%APP_NAME%", appName);
        values.put("%APP_NAME_ESCAPED%", appName.replace("'", "\\'"));
        values.put("%PACKAGE%", packageName);
        values.put("%PACKAGE_DIR%", packageDir);
        values.put("%MAIN_CLASS%", mainClass);
        values.put("%ANDROID_SDK%", sdkPath);
        values.put("%ASSET_PATH%", assetPath);
        values.put("%BUILD_TOOLS_VERSION%", DependencyBank.buildToolsVersion);
        values.put("%API_LEVEL%", DependencyBank.androidAPILevel);
        values.put("%GWT_VERSION%", DependencyBank.gwtVersion);
        if (builder.modules.contains(ProjectType.HTML)) {
            values.put("%GWT_INHERITS%", parseGwtInherits(builder));
        }

        copyAndReplace(outputDir, project, values);

        builder.cleanUp();

        // HACK executable flag isn't preserved for whatever reason...
        new File(outputDir, "gradlew").setExecutable(true);

        Executor.execute(new File(outputDir), "gradlew.bat", "gradlew", "clean" + parseGradleArgs(builder.modules, gradleArgs), callback);
    }

    private void copyAndReplace(String outputDir, Project project, Map<String, String> values) {
        File out = new File(outputDir);
        if (!out.exists() && !out.mkdirs()) {
            throw new RuntimeException("Couldn't create output directory '" + out.getAbsolutePath() + "'");
        }

        for (ProjectFile file : project.files) {
            copyFile(file, out, values);
        }
    }

    private byte[] readResource(String resource, String path) {
        InputStream in = null;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024 * 10];
            in = GdxSetup.class.getResourceAsStream(path + resource);
            if (in == null) throw new RuntimeException("Couldn't read resource '" + resource + "'");
            int read = 0;
            while ((read = in.read(buffer)) > 0) {
                bytes.write(buffer, 0, read);
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Couldn't read resource '" + resource + "'", e);
        } finally {
            if (in != null) try {
                in.close();
            } catch (IOException e) {
            }
        }
    }

    private byte[] readResource(File file) {
        InputStream in = null;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024 * 10];
            in = new FileInputStream(file);
            if (in == null)
                throw new RuntimeException("Couldn't read resource '" + file.getAbsoluteFile() + "'");
            int read = 0;
            while ((read = in.read(buffer)) > 0) {
                bytes.write(buffer, 0, read);
            }
            return bytes.toByteArray();
        } catch (Throwable e) {
            throw new RuntimeException("Couldn't read resource '" + file.getAbsoluteFile() + "'", e);
        } finally {
            if (in != null) try {
                in.close();
            } catch (IOException e) {
            }
        }
    }

    private String readResourceAsString(String resource, String path) {
        try {
            return new String(readResource(resource, path), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private String readResourceAsString(File file) {
        try {
            return new String(readResource(file), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeFile(File outFile, byte[] bytes) {
        OutputStream out = null;

        try {
            out = new BufferedOutputStream(new FileOutputStream(outFile));
            out.write(bytes);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't write file '" + outFile.getAbsolutePath() + "'", e);
        } finally {
            if (out != null) try {
                out.close();
            } catch (IOException e) {
            }
        }
    }

    private void writeFile(File outFile, String text) {
        try {
            writeFile(outFile, text.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private void copyFile(ProjectFile file, File out, Map<String, String> values) {
        File outFile = new File(out, file.outputName);
        if (!outFile.getParentFile().exists() && !outFile.getParentFile().mkdirs()) {
            throw new RuntimeException("Couldn't create dir '" + outFile.getAbsolutePath() + "'");
        }

        boolean isTemp = file instanceof TemporaryProjectFile ? true : false;

        if (file.isTemplate) {
            String txt;
            if (isTemp) {
                txt = readResourceAsString(((TemporaryProjectFile) file).file);
            } else {
                txt = readResourceAsString(file.resourceName, file.resourceLoc);
            }
            txt = replace(txt, values);
            writeFile(outFile, txt);
        } else {
            if (isTemp) {
                writeFile(outFile, readResource(((TemporaryProjectFile) file).file));
            } else {
                writeFile(outFile, readResource(file.resourceName, file.resourceLoc));
            }
        }
    }

    private String replace(String txt, Map<String, String> values) {
        for (String key : values.keySet()) {
            String value = values.get(key);
            txt = txt.replace(key, value);
        }
        return txt;
    }

    private static void printHelp() {
        System.out
                .println("Usage: GdxSetup --dir <dir-name> --name <app-name> --package <package> --mainClass <mainClass> --sdkLocation <SDKLocation>");
        System.out.println("dir ... the directory to write the project files to");
        System.out.println("name ... the name of the application");
        System.out.println("package ... the Java package name of the application");
        System.out.println("mainClass ... the name of your main ApplicationListener");
        System.out.println("sdkLocation ... the location of your android SDK. Uses ANDROID_HOME if not specified");
    }

    private static Map<String, String> parseArgs(String[] args) {
        if (args.length % 2 != 0) {
            printHelp();
            System.exit(-1);
        }

        Map<String, String> params = new HashMap<String, String>();
        for (int i = 0; i < args.length; i += 2) {
            String param = args[i].replace("--", "");
            String value = args[i + 1];
            params.put(param, value);
        }
        return params;
    }

    private String parseGwtInherits(ProjectBuilder builder) {
        String parsed = "";

        for (Dependency dep : builder.dependencies) {
            if (dep.getGwtInherits() != null) {
                for (String inherit : dep.getGwtInherits()) {
                    parsed += "\t<inherits name='" + inherit + "' />\n";
                }
            }
        }

        return parsed;
    }

    private String parseGradleArgs(List<ProjectType> modules, List<String> args) {
        String argString = "";
        if (args == null) return argString;
        for (String argument : args) {
            if (argument.equals("afterEclipseImport") && !modules.contains(ProjectType.DESKTOP))
                continue;
            argString += " " + argument;
        }
        return argString;
    }

    private boolean containsDependency(List<Dependency> dependencyList, ProjectDependency projectDependency) {
        for (Dependency dep : dependencyList) {
            if (dep.getName().equals(projectDependency.name())) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> params = parseArgs(args);
        if (!params.containsKey("dir") ||
                !params.containsKey("name") ||
                !params.containsKey("package") ||
                !params.containsKey("mainClass") ||
                ((!params.containsKey("sdkLocation") && System.getenv("ANDROID_HOME") == null))) {
            new GdxSetupUI();
            printHelp();
        } else {
            String sdkLocation = "";
            if (System.getenv("ANDROID_HOME") != null && !params.containsKey("sdkLocation")) {
                sdkLocation = System.getenv("ANDROID_HOME");
            } else {
                sdkLocation = params.get("sdkLocation");
            }

            DependencyBank bank = new DependencyBank();
            ProjectBuilder builder = new ProjectBuilder(bank);
            List<ProjectType> projects = new ArrayList<ProjectType>();
            projects.add(ProjectType.CORE);
            projects.add(ProjectType.DESKTOP);
            projects.add(ProjectType.ANDROID);
            projects.add(ProjectType.IOS);
            projects.add(ProjectType.HTML);

            List<DimensionType> dimensions = new ArrayList<DimensionType>();
            dimensions.add(DimensionType.THREE_DIMENSIONAL);

            List<ScreenType> screens = new ArrayList<ScreenType>();
            screens.add(ScreenType.LOADING);
            screens.add(ScreenType.MAIN);
            screens.add(ScreenType.GAME);

            List<Dependency> dependencies = new ArrayList<Dependency>();
            dependencies.add(bank.getDependency(ProjectDependency.GDX));
            dependencies.add(bank.getDependency(ProjectDependency.BULLET));

            builder.buildProject(projects, dependencies, dimensions, screens);
            builder.build();
            new GdxSetup().build(builder, params.get("dir"), params.get("name"), params.get("package"), params.get("mainClass"),
                    sdkLocation, new CharCallback() {
                        @Override
                        public void character(char c) {
                            System.out.print(c);
                        }
                    }, null);
        }
    }
}
