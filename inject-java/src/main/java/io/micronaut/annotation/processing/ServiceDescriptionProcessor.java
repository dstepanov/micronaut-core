/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.annotation.processing;

import io.micronaut.annotation.processing.visitor.JavaClassElement;
import io.micronaut.annotation.processing.visitor.JavaVisitorContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Generated;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.service.ServicesBundle;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder;
import io.micronaut.inject.configuration.ConfigurationMetadataWriter;
import io.micronaut.inject.visitor.VisitorConfiguration;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

import static io.micronaut.annotation.processing.AnnotationProcessingOutputVisitor.unwrapFilterOutputStream;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_8;

/**
 * A separate aggregating annotation processor responsible for creating META-INF/services entries.
 *
 * @author graemerocher
 * @since 2.0.0
 */
@SupportedOptions({
        AbstractInjectAnnotationProcessor.MICRONAUT_PROCESSING_INCREMENTAL,
        AbstractInjectAnnotationProcessor.MICRONAUT_PROCESSING_ANNOTATIONS
})
public class ServiceDescriptionProcessor extends AbstractInjectAnnotationProcessor {

    private final Map<String, Set<String>> serviceDescriptors = new HashMap<>();
    private final Map<String, Set<Element>> serviceElements = new HashMap<>();

    @Override
    protected String getIncrementalProcessorType() {
        return GRADLE_PROCESSING_AGGREGATING;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton("io.micronaut.core.annotation.Generated");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        List<io.micronaut.inject.ast.Element> originatingElements = new ArrayList<>();
        for (TypeElement annotation : annotations) {
            Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(annotation);
            for (Element element : elements) {
                if (element instanceof TypeElement) {
                    TypeElement typeElement = (TypeElement) element;
                    String name = typeElement.getQualifiedName().toString();
                    Generated generated = element.getAnnotation(Generated.class);
                    if (generated != null) {
                        String serviceName = generated.service();
                        if (StringUtils.isNotEmpty(serviceName)) {
                            serviceDescriptors.computeIfAbsent(serviceName, s1 -> new HashSet<>())
                                    .add(name);
                            serviceElements.computeIfAbsent(serviceName, s1 -> new HashSet<>())
                                    .add(element);
                            originatingElements.add(new JavaClassElement(typeElement, AnnotationMetadata.EMPTY_METADATA, null));
                        }
                    }

                }

            }
        }
        if (roundEnv.processingOver() && !serviceDescriptors.isEmpty()) {


//            serviceElements.forEach((serviceName, elements) -> {
//                if (elements.size() > 1) {
//                    writeServiceBundle(serviceName, elements);
//                }
//            });
//
//
//            System.out.println("Service descriptors: " + serviceDescriptors);

            classWriterOutputVisitor.writeServiceEntries(
                    serviceDescriptors,
                    originatingElements.toArray(io.micronaut.inject.ast.Element.EMPTY_ELEMENT_ARRAY)
            );

            writeConfigurationMetadata();
        }
        return true;
    }

    protected JavaVisitorContext newVisitorContext(@NonNull ProcessingEnvironment processingEnv) {
        return new JavaVisitorContext(
                processingEnv,
                messager,
                elementUtils,
                annotationUtils,
                typeUtils,
                modelUtils,
                genericUtils,
                filer,
                visitorAttributes
        ) {
            @NonNull
            @Override
            public VisitorConfiguration getConfiguration() {
                return new VisitorConfiguration() {
                    @Override
                    public boolean includeTypeLevelAnnotationsInGenericArguments() {
                        return false;
                    }
                };
            }
        };
    }

    private void writeServiceBundle(String serviceName, Set<Element> elements) {

        String classPackage = NameUtils.getPackageName(serviceName);
        String classSimpleName = NameUtils.getSimpleName(serviceName);

        String bundClassSimpleName = classSimpleName + "Bundle" + (Math.abs(new Random().nextInt()) % 10000);
        String bundleClassName = classPackage + "." + bundClassSimpleName;

        try {
            System.out.println(bundleClassName);
            Type owner = asType(bundleClassName);

            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            classWriter.visit(V1_8, ACC_PUBLIC, bundleClassName.replace('.', '/'), null, "java/lang/Object", new String[]{ServicesBundle.class.getName().replace('.', '/')});
            classWriter.visitAnnotation(Type.getType(Generated.class).getDescriptor(), false);

            int i = 0;
            List<String> serviceFields = new ArrayList<>();
            for (Element service : elements) {
                String serviceField = "service" + i++;
                serviceFields.add(serviceField);
                FieldVisitor fieldVisitor = classWriter.visitField(ACC_PRIVATE | ACC_STATIC, serviceField, asType(service.toString()).getDescriptor(), null, null);
                fieldVisitor.visitEnd();
            }

            MethodVisitor si = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            GeneratorAdapter staticInit = new GeneratorAdapter(si, ACC_STATIC, "<clinit>", "()V");

            i = 0;
            for (Element service : elements) {
                String serviceField = "service" + i++;
                Type type = asType(service.toString());
                staticInit.newInstance(type);
                staticInit.dup();
                staticInit.invokeConstructor(type, new Method("<init>", "()V"));
                staticInit.putStatic(owner, serviceField, type);
            }

            staticInit.visitInsn(RETURN);
            staticInit.visitMaxs(1, 1);
            staticInit.visitEnd();

            MethodVisitor ci = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            GeneratorAdapter constructorInit = new GeneratorAdapter(ci, ACC_PUBLIC, "<init>", "()V");
            constructorInit.loadThis();
            constructorInit.invokeConstructor(Type.getType(Object.class), new Method("<init>", "()V"));
            constructorInit.visitInsn(RETURN);
            constructorInit.visitMaxs(1, 1);
            constructorInit.visitEnd();

            String descriptor = new Method("process", Type.VOID_TYPE, new Type[]{Type.getType(Collection.class)}).getDescriptor();
            MethodVisitor processMethod = classWriter.visitMethod(ACC_PUBLIC, "populate", descriptor, null, null);
            GeneratorAdapter processMethodAdapter = new GeneratorAdapter(processMethod, ACC_PUBLIC, "process", descriptor);
            i = 0;
            for (Element service : elements) {
                String serviceField = "service" + i++;
                Type type = asType(service.toString());
                processMethodAdapter.loadArg(0);
                processMethodAdapter.getStatic(owner, serviceField, type);
                processMethodAdapter.invokeInterface(Type.getType(Collection.class), new Method("add", Type.getType(boolean.class), new Type[]{Type.getType(Object.class)}));
            }
            processMethodAdapter.visitInsn(RETURN);
            processMethodAdapter.visitMaxs(1, 1);
            processMethodAdapter.visitEnd();

            classWriter.visitEnd();

            JavaFileObject classFile = filer.createClassFile(bundleClassName, elements.toArray(new Element[0]));
            try (OutputStream os = unwrapFilterOutputStream(classFile.openOutputStream())) {
                os.write(classWriter.toByteArray());
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        serviceDescriptors.remove(serviceName);
        serviceDescriptors.put(serviceName + "$Bundle", Collections.singleton(bundleClassName));
    }

    private Type asType(String name) {
        return Type.getType("L" + name.replace(".", "/") + ";");
    }

    private void writeConfigurationMetadata() {
        ConfigurationMetadataBuilder.getConfigurationMetadataBuilder().ifPresent(metadataBuilder -> {
            try {
                if (metadataBuilder.hasMetadata()) {
                    ServiceLoader<ConfigurationMetadataWriter> writers = ServiceLoader.load(ConfigurationMetadataWriter.class, getClass().getClassLoader());

                    try {
                        for (ConfigurationMetadataWriter writer : writers) {
                            try {
                                writer.write(metadataBuilder, classWriterOutputVisitor);
                            } catch (IOException e) {
                                warning("Error occurred writing configuration metadata: %s", e.getMessage());
                            }
                        }
                    } catch (ServiceConfigurationError e) {
                        warning("Unable to load ConfigurationMetadataWriter due to : %s", e.getMessage());
                    }
                }
            } finally {
                ConfigurationMetadataBuilder.setConfigurationMetadataBuilder(null);
            }
        });

    }
}
