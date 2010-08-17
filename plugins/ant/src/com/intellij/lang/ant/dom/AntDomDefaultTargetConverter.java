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
package com.intellij.lang.ant.dom;

import com.intellij.lang.ant.AntSupport;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.CustomReferenceConverter;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 16, 2010
 */
public class AntDomDefaultTargetConverter extends Converter<TargetResolver.Result> implements CustomReferenceConverter<TargetResolver.Result>{

  @NotNull 
  public PsiReference[] createReferences(final GenericDomValue<TargetResolver.Result> value, PsiElement element, ConvertContext context) {
    return new PsiReference[] {new AntDomTargetReference(element, value)};
  }

  @Nullable
  public TargetResolver.Result fromString(@Nullable @NonNls String s, ConvertContext context) {
    final AntDomElement element = AntSupport.getInvocationAntDomElement(context);
    if (element != null && s != null) {
      final AntDomProject project = element.getAntProject();
      final TargetResolver.Result result = TargetResolver.resolve(project.getContextAntProject(), null, s);
      result.setRefsString(s);
      return result;
    }
    return null;
  }

  @Nullable
  public String toString(@Nullable TargetResolver.Result result, ConvertContext context) {
    return result != null? result.getRefsString() : null;
  }

}
