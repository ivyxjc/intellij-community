/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * User: anna
 * Date: Jul 30, 2010
 */
public class PsiDiamondType extends PsiType {
  private static final PsiType[] NULL_TYPES = new PsiType[]{NULL};
  private PsiManager myManager;
  private final PsiTypeElement myTypeElement;
  private static final Logger LOG = Logger.getInstance("#" + PsiDiamondType.class.getName());

  public PsiDiamondType(PsiManager manager, PsiTypeElement psiTypeElement) {
    super(PsiAnnotation.EMPTY_ARRAY);
    myManager = manager;
    myTypeElement = psiTypeElement;
  }

  @Override
  public String getPresentableText() {
    return "";
  }

  @Override
  public String getCanonicalText() {
    return "";
  }

  @Override
  public String getInternalCanonicalText() {
    return "Diamond Type";
  }

  @Override
  public boolean isValid() {
    return false;
  }

  @Override
  public boolean equalsToText(@NonNls String text) {
    return text != null && text.isEmpty();
  }

  @Override
  public <A> A accept(PsiTypeVisitor<A> visitor) {
    return visitor.visitType(this);
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    return GlobalSearchScope.allScope(myManager.getProject());
  }

  @NotNull
  @Override
  public PsiType[] getSuperTypes() {
    return new PsiType[]{getJavaLangObject(myManager, getResolveScope())};
  }

  public DiamondInferenceResult resolveInferredTypes() {
    final PsiNewExpression newExpression = PsiTreeUtil.getParentOfType(myTypeElement, PsiNewExpression.class);
    if (newExpression == null) {
      return PsiDiamondType.DiamondInferenceResult.NULL_RESULT;
    }

    return resolveInferredTypes(newExpression);
  }

  public static DiamondInferenceResult resolveInferredTypes(PsiNewExpression newExpression) {
    final PsiClass psiClass = findClass(newExpression);
    if (psiClass == null) return DiamondInferenceResult.NULL_RESULT;
    final PsiExpressionList argumentList = newExpression.getArgumentList();
    if (argumentList == null) return DiamondInferenceResult.NULL_RESULT;
    final PsiMethod constructor = findConstructor(psiClass, newExpression);
    PsiTypeParameter[] params = getAllTypeParams(constructor, psiClass);
    PsiMethod staticFactory = generateStaticFactory(constructor, psiClass, params);
    if (staticFactory == null) {
      return DiamondInferenceResult.NULL_RESULT;
    }
    final PsiSubstitutor inferredSubstitutor = inferTypeParametersForStaticFactory(staticFactory, newExpression);
    final PsiTypeParameter[] parameters = staticFactory.getTypeParameters();
    final PsiTypeParameter[] classParameters = psiClass.getTypeParameters();
    final PsiJavaCodeReferenceElement classOrAnonymousClassReference = newExpression.getClassOrAnonymousClassReference();
    LOG.assertTrue(classOrAnonymousClassReference != null);
    final DiamondInferenceResult result = new DiamondInferenceResult(classOrAnonymousClassReference.getReferenceName() + "<>");
    for (PsiTypeParameter parameter : parameters) {
      for (PsiTypeParameter classParameter : classParameters) {
        if (Comparing.strEqual(classParameter.getName(), parameter.getName())) {
          result.addInferredType(inferredSubstitutor.substitute(parameter));
          break;
        }
      }
    }
    return result;
  }


  @Nullable
  private static PsiMethod findConstructor(PsiClass containingClass, PsiNewExpression newExpression) {
    final PsiExpressionList argumentList = newExpression.getArgumentList();
    final Project project = newExpression.getProject();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final PsiResolveHelper resolveHelper = facade.getResolveHelper();
    final JavaResolveResult result =
      resolveHelper.resolveConstructor(facade.getElementFactory().createType(containingClass), argumentList, argumentList);
    return (PsiMethod)result.getElement();
  }

  @Nullable
  private static PsiClass findClass(PsiNewExpression newExpression) {
    final PsiJavaCodeReferenceElement classReference = newExpression.getClassOrAnonymousClassReference();
    if (classReference != null) {
      final String text = classReference.getReferenceName();
      if (text != null) {
        final Project project = newExpression.getProject();
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        final PsiResolveHelper resolveHelper = facade.getResolveHelper();
        return resolveHelper.resolveReferencedClass(text, newExpression);
      } else {
        return null;
      }
    }
    return null;
  }

  @Nullable
  private static PsiMethod generateStaticFactory(@Nullable PsiMethod constructor, PsiClass containingClass, PsiTypeParameter[] params) {
    final StringBuilder buf = new StringBuilder();
    buf.append("public static ");
    buf.append("<");
    buf.append(StringUtil.join(params, new Function<PsiTypeParameter, String>() {
      @Override
      public String fun(PsiTypeParameter psiTypeParameter) {
        return psiTypeParameter.getName();
      }
    }, ", "));
    buf.append(">");

    final String qualifiedName = containingClass.getQualifiedName();
    buf.append(qualifiedName != null ? qualifiedName : containingClass.getName());
    final PsiTypeParameter[] parameters = containingClass.getTypeParameters();
    buf.append("<");
    buf.append(StringUtil.join(parameters, new Function<PsiTypeParameter, String>() {
      @Override
      public String fun(PsiTypeParameter psiTypeParameter) {
        return psiTypeParameter.getName();
      }
    }, ", "));
    buf.append("> ");

    String staticFactoryName = "staticFactory";
    final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(containingClass.getProject());
    staticFactoryName = styleManager.suggestUniqueVariableName(staticFactoryName, containingClass, false);
    buf.append(staticFactoryName);
    if (constructor == null) {
      buf.append("()");
    }
    else {
      buf.append("(").append(StringUtil.join(constructor.getParameterList().getParameters(), new Function<PsiParameter, String>() {
        @Override
        public String fun(PsiParameter psiParameter) {
          return psiParameter.getType().getCanonicalText() + " " + psiParameter.getName();
        }
      }, ",")).append(")");
    }
    buf.append("{}");

    return JavaPsiFacade.getElementFactory(containingClass.getProject()).createMethodFromText(buf.toString(), constructor != null ? constructor : containingClass);
  }

  private static PsiTypeParameter[] getAllTypeParams(PsiTypeParameterListOwner listOwner, PsiClass containingClass) {
    Set<PsiTypeParameter> params = new LinkedHashSet<PsiTypeParameter>();
    if (listOwner != null) {
      Collections.addAll(params, listOwner.getTypeParameters());
    }
    Collections.addAll(params, containingClass.getTypeParameters());
    return params.toArray(new PsiTypeParameter[params.size()]);
  }


  private static PsiSubstitutor inferTypeParametersForStaticFactory(@NotNull PsiMethod staticFactoryMethod,
                                                                    PsiNewExpression expression) {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(staticFactoryMethod.getProject());
    final PsiResolveHelper resolveHelper = facade.getResolveHelper();
    final PsiParameter[] parameters = staticFactoryMethod.getParameterList().getParameters();
    final PsiExpressionList argumentList = expression.getArgumentList();
    final PsiExpression[] expressions = argumentList.getExpressions();
    return resolveHelper
      .inferTypeArguments(staticFactoryMethod.getTypeParameters(), parameters, expressions, PsiSubstitutor.EMPTY, expression, false);
  }

  public static class DiamondInferenceResult {
    public static final DiamondInferenceResult NULL_RESULT = new DiamondInferenceResult() {
      @Override
      public PsiType[] getTypes() {
        return NULL_TYPES;
      }

      @Override
      public String getErrorMessage() {
        return "Cannot infer arguments";
      }
    };

    private List<PsiType> myInferredTypes = new ArrayList<PsiType>();
    private String myErrorMessage;

    private String myNewExpressionPresentableText;

    public DiamondInferenceResult() {
    }

    public DiamondInferenceResult(String expressionPresentableText) {
      myNewExpressionPresentableText = expressionPresentableText;
    }

    public PsiType[] getTypes() {
      if (myErrorMessage != null) {
        return NULL_TYPES;
      }
      return myInferredTypes.toArray(new PsiType[myInferredTypes.size()]);
    }

    public String getErrorMessage() {
      return myErrorMessage;
    }

    public void addInferredType(PsiType psiType) {
      if (myErrorMessage != null) return;
      if (psiType == null) {
        myErrorMessage = "Cannot infer type arguments for " + myNewExpressionPresentableText;
      } else if (!isValid(psiType)) {
        myErrorMessage = "Cannot infer type arguments for " +
                         myNewExpressionPresentableText + " because type " + psiType.getPresentableText() + " inferred is not allowed in current context";
      } else {
        myInferredTypes.add(psiType);
      }
    }

    private static Boolean isValid(PsiType type) {
      return type.accept(new PsiTypeVisitor<Boolean>() {
        @Override
        public Boolean visitType(PsiType type) {
          return !(type instanceof PsiIntersectionType);
        }

        @Override
        public Boolean visitCapturedWildcardType(PsiCapturedWildcardType capturedWildcardType) {
          return false;
        }

        @Override
        public Boolean visitWildcardType(PsiWildcardType wildcardType) {
          final PsiType bound = wildcardType.getBound();
          if (bound != null) {
            if (bound instanceof PsiIntersectionType) return false;
            return bound.accept(this);
          }
          return true;
        }

        @Override
        public Boolean visitClassType(PsiClassType classType) {
          for (PsiType psiType : classType.getParameters()) {
            if (!psiType.accept(this)) {
              return false;
            }
          }
          return true;
        }
      });
    }
  }
}
