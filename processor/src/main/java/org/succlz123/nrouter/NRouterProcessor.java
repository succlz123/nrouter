package org.succlz123.nrouter;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

@AutoService(Processor.class)
public class NRouterProcessor extends BaseProcessor {
    private static final String TAG = NRouterProcessor.class.getName();
    private static final String MODULE_NAME_KEY = "n_router_name";

    private static final String PACKAGE_NAME = "org.succlz123.nrouter";
    private static final String MAPPER_CLASS_NAME = "NRouterMapper";
    private String classNameSuffix;
    private String generateClassName;

    private Map<String, PathClassParameter> pathClassHashMap = new HashMap<>();
    ArrayList<Map.Entry<String, PathClassParameter>> processList;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        Map<String, String> options = processingEnv.getOptions();
        for (String key : options.keySet()) {
            if (key.equals(MODULE_NAME_KEY)) {
                classNameSuffix = options.get(key);
                break;
            }
        }
        if (classNameSuffix == null) {
            classNameSuffix = "App";
        }
        generateClassName = MAPPER_CLASS_NAME + classNameSuffix;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        HashSet<String> supportTypes = new LinkedHashSet<>();
        supportTypes.add(Path.class.getCanonicalName());
        return supportTypes;
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        String tagStr = " -> processing " + classNameSuffix;
        messager.printMessage(Diagnostic.Kind.NOTE, TAG + tagStr);
        long startTime = System.currentTimeMillis();
        Set<? extends Element> pathElements = roundEnvironment.getElementsAnnotatedWith(Path.class);
        if (pathElements != null && !pathElements.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Element implElement : pathElements) {
                sb.append(implElement.getSimpleName());
                sb.append("\n");
            }
            messager.printMessage(Diagnostic.Kind.NOTE, TAG + tagStr + " - " + pathElements.size() + "\n" + sb.toString());
            pathClassHashMap.clear();
            for (Element implElement : pathElements) {
                if (implElement instanceof TypeElement) {
                    String fullName = implElement.asType().toString();
                    PathClassParameter proxy = pathClassHashMap.get(fullName);
                    if (proxy == null) {
                        Type superType = ((Symbol.ClassSymbol) implElement).getSuperclass();
                        boolean isActivity = false;
                        while (superType != null) {
                            if ("android.app.Activity".equals(superType.toString())) {
                                isActivity = true;
                                break;
                            }
                            if (superType instanceof Type.ClassType) {
                                Type curSuperType = ((Type.ClassType) superType).supertype_field;
                                if (curSuperType == null) {
                                    break;
                                }
                                superType = curSuperType;
                            } else {
                                break;
                            }
                        }
                        proxy = new PathClassParameter(elementUtils, implElement, isActivity);
                        pathClassHashMap.put(fullName, proxy);
                    }
                }
            }
            Set<Map.Entry<String, PathClassParameter>> entries = pathClassHashMap.entrySet();
            processList = new ArrayList<>(entries);
            if (processList.isEmpty()) {
                return false;
            }
            try {
                JavaFile javaFile = JavaFile.builder(PACKAGE_NAME, generateMapperCode()).build();
                javaFile.writeTo(processingEnv.getFiler());
            } catch (Exception e) {
                messager.printMessage(Diagnostic.Kind.ERROR, TAG + tagStr + " error " + e);
            }
            double cost = (System.currentTimeMillis() - startTime) / 1000d;
            messager.printMessage(Diagnostic.Kind.NOTE, TAG + tagStr + " finish cost time: " + cost + "s");
        }
        return true;
    }

    private TypeSpec generateMapperCode() {
        ClassName abstractMapper = ClassName.get(PACKAGE_NAME, "AbsRouterMapper");
        return TypeSpec.classBuilder(generateClassName)
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("This Java file is automatically generated by " + TAG + ", PLEASE DO NOT EDIT!")
                .superclass(abstractMapper)
                .addMethod(generateGetAllRouterPathInfoMethod())
                .addMethod(generateObtainInstanceMethod())
                .build();
    }

    private MethodSpec generateGetAllRouterPathInfoMethod() {
        ClassName pathInfo = ClassName.get(PACKAGE_NAME, "NRouterPathInfo");
        ClassName arrayList = ClassName.get(ArrayList.class);
        ParameterizedTypeName returnType = ParameterizedTypeName.get(arrayList, pathInfo);
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("getAllRouterPathInfo")
                .addAnnotation(ClassName.get(Override.class))
                .returns(returnType)
                .addModifiers(Modifier.PUBLIC);
        methodBuilder.addStatement("ArrayList<NRouterPathInfo> result = new ArrayList<>()", pathInfo);
        for (int i = 0; i < processList.size(); i++) {
            PathClassParameter pathClassParameter = processList.get(i).getValue();
            String pathName = pathClassParameter.classFullName.replace(".", "_");
            methodBuilder.addStatement("NRouterPathInfo " + pathName + " = new NRouterPathInfo()")
                    .addStatement(pathName + ".className = \"" + pathClassParameter.classFullName + "\"")
                    .addStatement(pathName + ".simpleClassName = \"" + pathClassParameter.classSimpleName + "\"")
                    .addStatement(pathName + ".routerMapper = this")
                    .addStatement(pathName + ".path = \"" + pathClassParameter.path + "\"");
            if (pathClassParameter.isActivity) {
                methodBuilder.addStatement(pathName + ".type = NRouterPathInfo.TYPE_ACTIVITY");
            }
            methodBuilder.addStatement("result.add(" + pathName + ")");
        }
        methodBuilder.addStatement("return result");
        return methodBuilder.build();
    }

    private MethodSpec generateObtainInstanceMethod() {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("obtainInstance")
                .addParameter(String.class, "className")
                .addAnnotation(ClassName.get(Override.class))
                .returns(Object.class)
                .addModifiers(Modifier.PUBLIC);
        for (Map.Entry<String, PathClassParameter> entry : processList) {
            if (entry.getValue().isActivity) {
                continue;
            }
            String classFullName = entry.getValue().classFullName;
            methodBuilder.beginControlFlow("if (className.equals(\"" + classFullName + "\"))");
            methodBuilder.addStatement("return new " + classFullName + "()");
            methodBuilder.endControlFlow();
        }
        methodBuilder.addStatement("return null");
        return methodBuilder.build();
    }

    private static class PathClassParameter {
        Element element;

        Path annotation;

        String packageName;
        String classSimpleName;
        String classFullName;

        String path;
        boolean isActivity;

        public PathClassParameter(Elements elementUtils, Element element, boolean isActivity) {
            this.element = element;
            this.annotation = element.getAnnotation(Path.class);
            this.path = this.annotation.path();
            PackageElement packageElement = elementUtils.getPackageOf(this.element);
            this.packageName = packageElement.getQualifiedName().toString();
            this.classSimpleName = element.getSimpleName().toString();
            this.classFullName = element.asType().toString();

            this.isActivity = isActivity;
        }
    }
}
