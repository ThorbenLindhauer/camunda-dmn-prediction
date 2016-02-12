/* Licensed under the Apache License, Version 2.0 (the "License");
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
package org.camunda.bpm.slacktime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.camunda.bpm.engine.impl.util.CollectionUtil;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.bpm.model.dmn.instance.Decision;
import org.camunda.bpm.model.dmn.instance.DecisionTable;
import org.camunda.bpm.model.dmn.instance.Input;
import org.camunda.bpm.model.dmn.instance.InputEntry;
import org.camunda.bpm.model.dmn.instance.Rule;

import com.github.thorbenlindhauer.factor.DiscreteFactor;
import com.github.thorbenlindhauer.network.DiscreteFactorBuilder;
import com.github.thorbenlindhauer.network.DiscreteModelBuilder;
import com.github.thorbenlindhauer.network.GraphicalModel;
import com.github.thorbenlindhauer.network.ModelBuilder;
import com.github.thorbenlindhauer.network.ScopeBuilder;
import com.github.thorbenlindhauer.variable.Scope;

/**
 * @author Thorben Lindhauer
 *
 */
public class CanonicalDmnModel {

  protected GraphicalModel<DiscreteFactor> graphicalModel;
  protected VariableIndex variableIndex;

  public static final String RULE_VARIABLE_NAME = "$rule";

  public static CanonicalDmnModel fromDmnModelInstance(String dmnModelId,
      DmnModelInstance modelInstance, InputDistributionSource distributionSource) {
    CanonicalDmnModel model = new CanonicalDmnModel();

    Collection<Decision> decisions = modelInstance.getModelElementsByType(Decision.class);

    // TODO: handle more than one decision per file

    // TODO: check that at least one decision is present
    Decision decision = decisions.iterator().next();


    Collection<DecisionTable> decisionTables = decision.getChildElementsByType(DecisionTable.class);
    DecisionTable decisionTable = decisionTables.iterator().next();

    // TODO: parse outputs and add them as variables
    Collection<Input> inputs = decisionTable.getInputs();
    Collection<Rule> rules = decisionTable.getRules();
    TreeSet<String> sortedInputs = new TreeSet<String>();

    for (Input input : inputs) {
      sortedInputs.add(input.getLabel());
    }

    // determine distinct input values
    Map<String, Set<String>> variableValuesByName = new HashMap<String, Set<String>>();
    for (Rule rule : rules) {
      Iterator<Input> inputIt = inputs.iterator();
      Iterator<InputEntry> inputEntryIt = rule.getInputEntries().iterator();

      while (inputIt.hasNext() && inputEntryIt.hasNext()) {
        Input input = inputIt.next();
        InputEntry inputEntry = inputEntryIt.next();
        String inputValue = inputEntry.getTextContent();

        CollectionUtil.addToMapOfSets(variableValuesByName, input.getLabel(), inputValue);
      }

      CollectionUtil.addToMapOfSets(variableValuesByName, RULE_VARIABLE_NAME, rule.getId());
    }

    // order input values canonically
    Map<String, List<String>> sortedVariableValues = new HashMap<String, List<String>>();
    for (String input : variableValuesByName.keySet()) {
      List<String> sortedValuesForInput = new ArrayList<String>(variableValuesByName.get(input));
      Collections.sort(sortedValuesForInput);
      sortedVariableValues.put(input, sortedValuesForInput);
    }
    model.variableIndex = new VariableIndex(sortedVariableValues);

    ScopeBuilder scopeBuilder = GraphicalModel.create();
    for (String input : sortedInputs) {
      scopeBuilder.discreteVariable(input, variableValuesByName.get(input).size());
    }

    scopeBuilder.discreteVariable(RULE_VARIABLE_NAME, rules.size());
    Scope networkScope = scopeBuilder.buildScope();

    ModelBuilder<DiscreteFactor, DiscreteFactorBuilder<DiscreteModelBuilder>> networkBuilder =
        scopeBuilder.discreteNetwork();

    // build distributions P(A) where A is an input
    for (Input input : inputs) {
      Distribution distribution = distributionSource.getDistribution(dmnModelId, input.getLabel());

      // values in canonical order
      List<String> sortedValues = sortedVariableValues.get(input.getLabel());
      double[] table = new double[sortedValues.size()];

      int i = 0;
      for (String inputValue : sortedValues) {
        table[i] = distribution.getProbability(inputValue);
        i++;
      }

      networkBuilder = networkBuilder
        .factor()
        .scope(input.getLabel())
        .basedOnTable(table);
    }

    // build distribution P($rule | all inputs)
    DiscreteFactorBuilder<DiscreteModelBuilder> ruleFactorBuilder = networkBuilder.factor()
        .scope(RULE_VARIABLE_NAME);
    for (Input input : inputs) {
      ruleFactorBuilder = ruleFactorBuilder.scope(input.getLabel());
    }

    ProbabilityTableBuilder tableBuilder = new ProbabilityTableBuilder(networkScope, model.variableIndex);

    for (Rule rule : rules) {
      Map<String, String> inputAssignment = new HashMap<String, String>();
      inputAssignment.put(RULE_VARIABLE_NAME, rule.getId());

      Iterator<Input> inputIt = inputs.iterator();
      Iterator<InputEntry> inputEntryIt = rule.getInputEntries().iterator();

      while (inputIt.hasNext() && inputEntryIt.hasNext()) {
        inputAssignment.put(inputIt.next().getLabel(), inputEntryIt.next().getTextContent());
      }

      tableBuilder.submitValue(inputAssignment, 1.0d);
    }

    networkBuilder = ruleFactorBuilder.basedOnTable(tableBuilder.getTable());

    model.graphicalModel = networkBuilder.build();

    return model;
  }

  public Scope toScope(Evidence evidence) {
    return graphicalModel.getScope().subScope(evidence.getVariables());
  }

  public int[] toCanonicalAssignment(Evidence evidence) {
    Scope evidenceScope = toScope(evidence);
    int[] assignment = new int[evidenceScope.size()];
    String[] scopeVariables = evidenceScope.getVariableIds();

    for (int i = 0; i < scopeVariables.length; i++) {
      String variable = scopeVariables[i];
      assignment[i] = variableIndex.getIndex(variable, evidence.getVariableAssignment(variable));
    }

    return assignment;
  }
}
