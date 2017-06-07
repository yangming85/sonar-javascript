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
package org.sonar.javascript.metrics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonar.javascript.tree.KindSet;
import org.sonar.javascript.tree.impl.JavaScriptTree;
import org.sonar.plugins.javascript.api.tree.ScriptTree;
import org.sonar.plugins.javascript.api.tree.Tree;
import org.sonar.plugins.javascript.api.tree.Tree.Kind;
import org.sonar.plugins.javascript.api.tree.declaration.AccessorMethodDeclarationTree;
import org.sonar.plugins.javascript.api.tree.declaration.FunctionDeclarationTree;
import org.sonar.plugins.javascript.api.tree.declaration.FunctionTree;
import org.sonar.plugins.javascript.api.tree.declaration.MethodDeclarationTree;
import org.sonar.plugins.javascript.api.tree.expression.ArrowFunctionTree;
import org.sonar.plugins.javascript.api.tree.expression.BinaryExpressionTree;
import org.sonar.plugins.javascript.api.tree.expression.ConditionalExpressionTree;
import org.sonar.plugins.javascript.api.tree.expression.ExpressionTree;
import org.sonar.plugins.javascript.api.tree.expression.FunctionExpressionTree;
import org.sonar.plugins.javascript.api.tree.expression.ParenthesisedExpressionTree;
import org.sonar.plugins.javascript.api.tree.lexical.SyntaxToken;
import org.sonar.plugins.javascript.api.tree.statement.BreakStatementTree;
import org.sonar.plugins.javascript.api.tree.statement.CatchBlockTree;
import org.sonar.plugins.javascript.api.tree.statement.ContinueStatementTree;
import org.sonar.plugins.javascript.api.tree.statement.DoWhileStatementTree;
import org.sonar.plugins.javascript.api.tree.statement.ElseClauseTree;
import org.sonar.plugins.javascript.api.tree.statement.ForObjectStatementTree;
import org.sonar.plugins.javascript.api.tree.statement.ForStatementTree;
import org.sonar.plugins.javascript.api.tree.statement.IfStatementTree;
import org.sonar.plugins.javascript.api.tree.statement.SwitchStatementTree;
import org.sonar.plugins.javascript.api.tree.statement.WhileStatementTree;
import org.sonar.plugins.javascript.api.visitors.DoubleDispatchVisitor;
import org.sonar.plugins.javascript.api.visitors.IssueLocation;
import org.sonar.plugins.javascript.api.visitors.SubscriptionVisitor;

import static com.sun.tools.internal.xjc.reader.Ring.add;
import static org.sonar.plugins.javascript.api.tree.Tree.Kind.CONDITIONAL_AND;
import static org.sonar.plugins.javascript.api.tree.Tree.Kind.CONDITIONAL_OR;

public class CognitiveComplexity extends DoubleDispatchVisitor {
  private int nestingLevel;
  private int ownComplexity;
  private int nestedFunctionComplexity;

  private boolean functionContainsStructuralComplexity;

  private List<IssueLocation> ownIssueLocations = new ArrayList<>();
  private List<IssueLocation> nestedFunctionsIssueLocations = new ArrayList<>();

  private Tree currentFunction = null;
  private Set<FunctionTree> nestedFunctions = new HashSet<>();
  private Deque<Tree> functionStack = new ArrayDeque<>();
  private Set<Tree> logicalOperationsToIgnore = new HashSet<>();

  public ComplexityData calculateComplexity(Tree functionTree) {
    functionTree.accept(this);
    return buildComplexityData();
  }

  public ComplexityData calculateScriptComplexity(ScriptTree tree) {
    List<FunctionTree> functions = FunctionVisitor.collectAllFunctions(tree);
    Set<CognitiveComplexity.ComplexityData> complexities = new HashSet<>();
    Set<FunctionTree> alreadyProcessedFunctions = new HashSet<>();
    for (FunctionTree function : functions) {
      if(!alreadyProcessedFunctions.contains(function)) {
        ComplexityData complexityData = new CognitiveComplexity().calculateComplexity(function);
        complexities.add(complexityData);
        alreadyProcessedFunctions.addAll(complexityData.aggregatedNestedFunctions());
      }
    }
    Integer fileComplexity = complexities.stream().map(ComplexityData::complexity).reduce(0, Integer::sum);
    List<IssueLocation> locations = complexities.stream().flatMap(data -> data.secondaryLocations().stream()).collect(Collectors.toList());
    return new ComplexityData(fileComplexity, locations, Collections.emptySet());
  }

  public void clear() {
    currentFunction = null;
  }

  private void initComplexityCalculation(Tree tree) {
    nestingLevel = 0;
    ownIssueLocations = new ArrayList<>();
    nestedFunctionsIssueLocations = new ArrayList<>();
    ownComplexity = 0;
    nestedFunctionComplexity = 0;
    functionContainsStructuralComplexity = false;
    currentFunction = tree;
    nestedFunctions.clear();
    logicalOperationsToIgnore.clear();

    functionStack.clear();
    functionStack.push(currentFunction);
  }

  private ComplexityData buildComplexityData() {
    int complexity;
    Set<FunctionTree> aggregatedNestedFunctions = new HashSet<>();
    List<IssueLocation> allIssueLocations = new ArrayList<>(ownIssueLocations);

    if (functionContainsStructuralComplexity) {
      complexity = ownComplexity + nestedFunctionComplexity;
      aggregatedNestedFunctions.addAll(nestedFunctions);
      allIssueLocations.addAll(nestedFunctionsIssueLocations);
    } else {
      complexity = ownComplexity;
    }

    return new ComplexityData(complexity, allIssueLocations, aggregatedNestedFunctions);
  }

  @Override
  public void visitIfStatement(IfStatementTree tree) {
    if (isElseIf(tree)) {
      addComplexityWithoutNesting(tree.ifKeyword());

    } else {
      addComplexityWithNesting(tree.ifKeyword());
    }

    visit(tree.condition());
    visitWithNesting(tree.statement());
    visit(tree.elseClause());
  }

  @Override
  public void visitElseClause(ElseClauseTree tree) {
    if (tree.statement().is(Tree.Kind.IF_STATEMENT)) {
      visit(tree.statement());

    } else {
      addComplexityWithoutNesting(tree.elseKeyword());
      visitWithNesting(tree.statement());
    }
  }

  @Override
  public void visitWhileStatement(WhileStatementTree tree) {
    visitLoop(tree.whileKeyword(), tree.statement(), tree.condition());
  }

  @Override
  public void visitDoWhileStatement(DoWhileStatementTree tree) {
    visitLoop(tree.doKeyword(), tree.statement(), tree.condition());
  }

  @Override
  public void visitForStatement(ForStatementTree tree) {
    visitLoop(tree.forKeyword(), tree.statement(), tree.init(), tree.condition(), tree.update());
  }

  @Override
  public void visitForObjectStatement(ForObjectStatementTree tree) {
    visitLoop(tree.forKeyword(), tree.statement(), tree.variableOrExpression(), tree.expression());
  }

  private void visitLoop(SyntaxToken secondaryLocationToken, Tree loopBody, Tree... notNestedElements) {
    addComplexityWithNesting(secondaryLocationToken);
    visit(notNestedElements);
    visitWithNesting(loopBody);
  }

  @Override
  public void visitCatchBlock(CatchBlockTree tree) {
    addComplexityWithNesting(tree.catchKeyword());
    visitWithNesting(tree.block());
  }

  @Override
  public void visitSwitchStatement(SwitchStatementTree tree) {
    addComplexityWithNesting(tree.switchKeyword());
    nestingLevel++;
    super.visitSwitchStatement(tree);
    nestingLevel--;
  }

  @Override
  public void visitBinaryExpression(BinaryExpressionTree tree) {
    if (tree.is(CONDITIONAL_AND, CONDITIONAL_OR)) {
      JavaScriptTree javaScriptTree = (JavaScriptTree) tree;

      ExpressionTree leftChild = removeParenthesis(tree.leftOperand());
      ExpressionTree rightChild = removeParenthesis(tree.rightOperand());

      boolean leftChildOfSameKind = leftChild.is(javaScriptTree.getKind());
      boolean rightChildOfSameKind = rightChild.is(javaScriptTree.getKind());

      // For expressions with same-kind operators like "a && (b && c)" we want to have secondary location on leftmost operator
      // So we "ignore" right operand
      if (rightChildOfSameKind) {
        logicalOperationsToIgnore.add(rightChild);
      }

      // And we add complexity for leftmost operator
      if (!logicalOperationsToIgnore.contains(tree) && !leftChildOfSameKind) {
        addComplexityWithoutNesting(tree.operatorToken());
      }

    }

    super.visitBinaryExpression(tree);
  }

  private static ExpressionTree removeParenthesis(ExpressionTree expressionTree) {
    if (expressionTree.is(Tree.Kind.PARENTHESISED_EXPRESSION)) {
      return removeParenthesis(((ParenthesisedExpressionTree) expressionTree).expression());
    } else {
      return expressionTree;
    }
  }

  @Override
  public void visitConditionalExpression(ConditionalExpressionTree tree) {
    addComplexityWithNesting(tree.queryToken());

    visit(tree.condition());

    visitWithNesting(tree.trueExpression());
    visitWithNesting(tree.falseExpression());
  }

  @Override
  public void visitBreakStatement(BreakStatementTree tree) {
    visitJumpStatement(tree.breakKeyword(), tree.labelToken());
    super.visitBreakStatement(tree);
  }

  @Override
  public void visitContinueStatement(ContinueStatementTree tree) {
    visitJumpStatement(tree.continueKeyword(), tree.labelToken());
    super.visitContinueStatement(tree);
  }

  private void visitJumpStatement(SyntaxToken keyword, @Nullable SyntaxToken label) {
    if (label != null) {
      addComplexityWithoutNesting(keyword);
    }
  }

  private void visit(Tree... trees) {
    for (Tree tree : trees) {
      if (tree != null) {
        tree.accept(this);
      }
    }
  }

  private void visitWithNesting(Tree tree) {
    nestingLevel++;
    tree.accept(this);
    nestingLevel--;
  }

  private void addComplexityWithNesting(SyntaxToken secondaryLocationToken) {
    if (functionStack.peek().equals(currentFunction)) {
      functionContainsStructuralComplexity = true;
    }
    addComplexity(nestingLevel + 1, secondaryLocationToken);
  }

  private void addComplexityWithoutNesting(SyntaxToken secondaryLocationToken) {
    addComplexity(1, secondaryLocationToken);
  }

  private void addComplexity(int addedComplexity, SyntaxToken secondaryLocationToken) {
    IssueLocation secondaryLocation = new IssueLocation(secondaryLocationToken, secondaryMessage(addedComplexity));
    if (functionStack.peek().equals(currentFunction)) {
      ownComplexity += addedComplexity;
      ownIssueLocations.add(secondaryLocation);
    } else {
      nestedFunctionComplexity += addedComplexity;
      nestedFunctionsIssueLocations.add(secondaryLocation);
    }
  }

  private static String secondaryMessage(int complexity) {
    if (complexity == 1) {
      return "+1";

    } else {
      return String.format("+%s (incl. %s for nesting)", complexity, complexity - 1);
    }
  }

  @Override
  public void visitFunctionDeclaration(FunctionDeclarationTree tree) {
      visitFunction(tree);

      super.visitFunctionDeclaration(tree);
      leaveFunction(tree);
  }

  @Override
  public void visitArrowFunction(ArrowFunctionTree tree) {
      visitFunction(tree);

      super.visitArrowFunction(tree);

      leaveFunction(tree);
  }

  @Override
  public void visitFunctionExpression(FunctionExpressionTree tree) {
    visitFunction(tree);
    super.visitFunctionExpression(tree);
    leaveFunction(tree);
  }

  @Override
  public void visitMethodDeclaration(MethodDeclarationTree tree) {
    visitFunction(tree);
    super.visitMethodDeclaration(tree);
    leaveFunction(tree);
  }

  @Override
  public void visitAccessorMethodDeclaration(AccessorMethodDeclarationTree tree) {
    visitFunction(tree);
    super.visitAccessorMethodDeclaration(tree);
    leaveFunction(tree);
  }

  private void visitFunction(FunctionTree tree) {
    if (currentFunction == null) {
      initComplexityCalculation(tree);

    } else {
      nestedFunctions.add(tree);
      functionStack.push(tree);
      nestingLevel++;
    }
  }

  private void leaveFunction(FunctionTree tree) {
    if (tree.equals(currentFunction)) {
      currentFunction = null;

    } else {
      nestingLevel--;
      functionStack.pop();
    }
  }

  private static boolean isElseIf(IfStatementTree tree) {
    return tree.parent().is(Tree.Kind.ELSE_CLAUSE);
  }

  public static class ComplexityData {
    private int complexity;
    private List<IssueLocation> secondaryLocations;
    private Set<FunctionTree> aggregatedNestedFunctions;

    ComplexityData(int complexity, List<IssueLocation> secondaryLocations, Set<FunctionTree> aggregatedNestedFunctions) {
      this.complexity = complexity;
      this.secondaryLocations = secondaryLocations;
      this.aggregatedNestedFunctions = aggregatedNestedFunctions;
    }

    public int complexity() {
      return complexity;
    }

    public List<IssueLocation> secondaryLocations() {
      return secondaryLocations;
    }

    public Set<FunctionTree> aggregatedNestedFunctions() {
      return aggregatedNestedFunctions;
    }
  }

  private static class FunctionVisitor extends SubscriptionVisitor {
    private List<FunctionTree> collectedFunctions = new ArrayList<>();

    public static List<FunctionTree> collectAllFunctions(ScriptTree tree) {
      FunctionVisitor functionVisitor = new FunctionVisitor();
      functionVisitor.scanTree(tree);
      return functionVisitor.collectedFunctions;
    }

    @Override
    public Set<Kind> nodesToVisit() {
      return KindSet.FUNCTION_KINDS.getSubKinds();
    }

    @Override
    public void visitNode(Tree tree) {
      collectedFunctions.add((FunctionTree) tree);
    }
  }
}
