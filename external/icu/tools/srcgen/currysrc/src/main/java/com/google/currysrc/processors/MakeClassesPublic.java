/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.currysrc.processors;

import com.google.common.collect.Lists;
import com.google.currysrc.api.process.Context;
import com.google.currysrc.api.process.JavadocUtils;
import com.google.currysrc.api.process.Processor;
import com.google.currysrc.api.process.ast.TypeLocator;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

/**
 * Makes classes public.
 */
public final class MakeClassesPublic implements Processor {

  private final List<TypeLocator> whitelist;

  private final String reason;

  public MakeClassesPublic(List<TypeLocator> whitelist, String reason) {
    this.whitelist = whitelist;
    this.reason = reason;
  }

  @Override public final void process(Context context, CompilationUnit cu) {
    final List<AbstractTypeDeclaration> toMakePublic = new ArrayList<>();
    cu.accept(new ASTVisitor() {
      @Override
      public boolean visit(TypeDeclaration node) {
        return visitAbstract(node);
      }

      @Override
      public boolean visit(EnumDeclaration node) {
        return visitAbstract(node);
      }

      private boolean visitAbstract(AbstractTypeDeclaration node) {
        for (TypeLocator whitelistedType : whitelist) {
          if (whitelistedType.matches(node)) {
            toMakePublic.add(node);
            break;
          }
        }
        return false;
      }
    });
    ASTRewrite rewrite = context.rewrite();
    for (AbstractTypeDeclaration node : Lists.reverse(toMakePublic)) {
      JavadocUtils.addJavadocTag(rewrite, node, reason);
      ChildListPropertyDescriptor modifiersProperty = node.getModifiersProperty();
      AST ast = rewrite.getAST();
      ListRewrite listRewrite = rewrite.getListRewrite(node, modifiersProperty);
      // This will not work properly if the node already has a modifier such as protected or
      // private but as this is intended for handling package private classes that is not an issue.
      listRewrite.insertAt(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD), 0, null);
    }
  }
}
