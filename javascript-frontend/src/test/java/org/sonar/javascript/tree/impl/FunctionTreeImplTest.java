/*
 * SonarQube JavaScript Plugin
 * Copyright (C) 2011-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.javascript.tree.impl;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.javascript.tree.impl.declaration.FunctionTreeImpl;
import org.sonar.javascript.utils.TestInputFile;
import org.sonar.javascript.visitors.JavaScriptVisitorContext;
import org.sonar.plugins.javascript.api.tree.Tree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.javascript.utils.TestUtils.createContext;

public class FunctionTreeImplTest {

  @Test
  public void should_return_outer_scope_symbol_usages() throws Exception {
    InputFile inputFile = new TestInputFile("src/test/resources/tree/", "outer_scope_variables.js");
    final JavaScriptVisitorContext context = createContext(inputFile);
    FunctionTreeImpl functionTree = (FunctionTreeImpl) context.getTopTree().items().items().stream().filter(tree -> tree.is(Tree.Kind.FUNCTION_DECLARATION)).findFirst().get();
    Set<String> usages = functionTree.outerScopeSymbolUsages().map(usage -> usage.identifierTree().name()).collect(Collectors.toSet());
    assertThat(usages).containsExactlyInAnyOrder("a", "b", "writeOnly");
  }

}
