package com.wakao.maindexkeep

import com.droid.multidex.ClassReferenceListBuilder
import com.google.common.base.Charsets
import com.google.common.collect.Maps
import com.google.common.io.Files
import com.wakao.maindexkeep.task.GenerateMainDexListTask
import org.gradle.api.*

public class MainDexListPlugin implements Plugin<Project> {
    File classesJar;

    File inputObfuscatedFilter;

    File outputMainDexList;

    File mappingFile;

    File inputFilter;

    Task transformClassesWithMultidexlist;

    void apply(final Project project) {
        inputFilter = new File("$project.rootDir\\main_dex_gen_filter.txt");
        println('===================================')
        println('main dex list generator start(inputFilter ' + inputFilter + ').....')
        println('===================================')

        project.tasks.whenTaskAdded { task ->
            def android = project.extensions.android
            android.applicationVariants.all { variant ->
                def taskName = task.name
                def variantName = variant.name.capitalize()
                if (variantName.contains('Release')
                        && taskName == "transformClassesWithDexFor${variantName}") {
                    println('task added ' + task)
                    task.doFirst {
                        println('task do begin....')
                    }
                }
            }
        }

        project.afterEvaluate {
            if (!project.plugins.hasPlugin('com.android.application')) {
                throw new GradleException("android application plguin required")
            }

            def android = project.extensions.android
            //open jumboMode
            android.dexOptions.jumboMode = true
            //close preDexLibraries
            try {
                android.dexOptions.preDexLibraries = false
            } catch (Throwable e) {
                //no preDexLibraries field, just continue
            }

            android.applicationVariants.all { variant ->

                def variantOutput = variant.outputs.first()
                println('variantOutput first ' + variantOutput)

                def variantName = variant.name.capitalize()
                if (variantName.contains('Release')) {
                    try {
                        def instantRunTask = project.tasks.getByName("transformClassesWithInstantRunFor${variantName}")
                        if (instantRunTask) {
                            throw new GradleException(
                                    "Tinker does not support instant run mode, please trigger build"
                                            + " by assemble${variantName} or disable instant run"
                                            + " in 'File->Settings...'."
                            )
                        }
                    } catch (UnknownTaskException e) {
                        // Not in instant run mode, continue.
                    }

                    Task transformClassesAndResourcesWithProguardForVariant =
                            project.tasks.getByName("transformClassesAndResourcesWithProguardFor${variantName}")

                    def inputs = transformClassesAndResourcesWithProguardForVariant.inputs.files;
                    def outputs = transformClassesAndResourcesWithProguardForVariant.outputs.files;
                    inputs.each {
                        println('transformClassesAndResourcesWithProguardForVariant inputs ' + it)
                    }
                    outputs.each {
                        if (it.absolutePath.contains('mapping.txt')) {
                            mappingFile = it;
                            println('mapping file ' + it)
                        }
                        println('transformClassesAndResourcesWithProguardForVariant output ' + it)
                    }

                    Task transformClassesWithMultidexlistForVariant =
                            project.tasks.getByName("transformClassesWithMultidexlistFor${variantName}");
                    inputs = transformClassesWithMultidexlistForVariant.inputs.files;
                    outputs = transformClassesWithMultidexlistForVariant.outputs.files;
                    transformClassesWithMultidexlist = transformClassesWithMultidexlistForVariant;
                    inputs.each {
                        println('transformClassesWithMultidexlistForVariant inputs ' + it)
                    }
                    outputs.each {
                        if (it.absolutePath.contains('maindexlist.txt')) {
                            outputMainDexList = it;
                            println('output main dex list file ' + it)
                        }
                        println('transformClassesWithMultidexlistForVariant output ' + it)
                    }

                    Task transformClassesWithDexForVariant =
                            project.tasks.getByName("transformClassesWithDexFor${variantName}")
                    inputs = transformClassesWithDexForVariant.inputs.files;
                    outputs = transformClassesWithDexForVariant.outputs.files;
                    inputs.each {
                        println('transformClassesWithDexForVariant inputs ' + it)
                    }
                    outputs.each {
                        println('transformClassesWithDexForVariant output ' + it)
                    }

                    GenerateMainDexListTask generateMainDexListTask =
                            project.tasks.create("generateMainDexList${variantName}", GenerateMainDexListTask)
                    transformClassesWithDexForVariant.getActions().each {
                        println('transformClassesWithDexForVariant actions ' + it.metaClass)
                    }

                    transformClassesWithDexForVariant.doFirst {
                        println('2*********************************2')
                        println('begin execute generate main dex list')
                        println('***********************************')
                        Set<File> files = new HashSet<>();
                        getInputs().files.each {
                            filterInputJar(files, it)
                            println('input jar size ' + files.size())
                            classesJar = files.iterator().next()
                        }

                        List<String> obfuscatedClasses = getObfuscatedClasses(mappingFile, inputFilter);
                        inputObfuscatedFilter = new File("${project.buildDir}\\intermediates\\multi-dex\\"
                                + variantName + "\\main_dex_gen_filter.txt");
                        if(inputObfuscatedFilter.exists())
                            inputObfuscatedFilter.delete()
                        inputObfuscatedFilter.getParentFile().mkdirs()
                        inputObfuscatedFilter.createNewFile()

                        for (String line : obfuscatedClasses) {
                            String replaceLine = line.replace(".", "/");
                            println("---generate main dex output replaceLine " + replaceLine + "---");
                            Files.append(replaceLine, inputObfuscatedFilter, Charsets.UTF_8);
                            Files.append("\n", inputObfuscatedFilter, Charsets.UTF_8);
                        }
                        println('begin to generate maindexlist classesJar ' + classesJar
                                + 'inputObfuscatedFilter ' + inputObfuscatedFilter
                                + 'outputMainDexList ' + outputMainDexList)

                        ClassReferenceListBuilder.main("--disable-annotation-resolution-workaround",
                                classesJar.absolutePath, inputObfuscatedFilter.absolutePath,
                                outputMainDexList.absolutePath);
                    }
                }
            }
        }
    }

    void filterInputJar(Set<File> files, File file) {
        if (file.isDirectory()) {
            file.listFiles(new FileFilter() {
                @Override
                boolean accept(File pathname) {
                    if (pathname.isFile()) {
                        println('filter input jar ' + pathname)
                        files.add(pathname);
                        return true;
                    } else {
                        filterInputJar(files, pathname)
                    }
                    return false;
                }
            })
        }
    }

    def getObfuscatedClasses(File mappingFiles, File input) {
        if (mappingFiles == null || !mappingFiles.isFile() || input == null) {
            return new ArrayList<String>();
        }
        List<String> keep_lines = Files.readLines(input, Charsets.UTF_8);
        List<String> lines = Files.readLines(mappingFiles, Charsets.UTF_8);

        Map<String, String> map = Maps.newHashMap();
        for (String line : lines) {
            if (line.startsWith(" ")) {
                continue;
            }
            int pos = line.indexOf(" -> ");
            if (pos == -1) {
                throw new RuntimeException("unable to read mapping file.");
            }
            String fullName = line.substring(0, pos);
            String obfuscatedName = line.substring(pos + 4, line.size() - 1);
            map.put(fullName, obfuscatedName);
        }

        List<String> obfuscatedClass = new ArrayList<String>();
        for (String keepLine : keep_lines) {
            String obfuscatedClz = map.get(keepLine);
            if (obfuscatedClz != null && obfuscatedClz.length() > 0) {
                obfuscatedClass.add(obfuscatedClz);
            }
        }
        return obfuscatedClass;
    }
}