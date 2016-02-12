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
package org.camunda.bpm.slacktime.processengine;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.history.HistoricDecisionInstance;
import org.camunda.bpm.engine.history.HistoricDecisionOutputInstance;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.bpm.model.dmn.instance.DecisionTable;
import org.camunda.bpm.model.dmn.instance.Input;
import org.camunda.bpm.model.dmn.instance.InputEntry;
import org.camunda.bpm.model.dmn.instance.Rule;
import org.camunda.bpm.slacktime.Distribution;
import org.camunda.bpm.slacktime.DistributionSource;
import org.camunda.bpm.slacktime.VariableIndex;

import com.github.thorbenlindhauer.learning.prior.ConditionalDiscreteDistributionPrior;
import com.github.thorbenlindhauer.learning.prior.UniformDirichletPriorInitializer;
import com.github.thorbenlindhauer.network.ScopeBuilderImpl;
import com.github.thorbenlindhauer.variable.Scope;

/**
 * @author Thorben Lindhauer
 *
 */
public class HistoryDistributionSource implements DistributionSource {

  protected HistoryService historyService;
  protected RepositoryService repositoryService;

  public HistoryDistributionSource(ProcessEngine engine) {
    this.historyService = engine.getHistoryService();
    this.repositoryService = engine.getRepositoryService();
  }

  public Distribution getDistribution(String dmnModelId, String variable, VariableIndex variableIndex) {

    // TODO: validate that decision definition has hit policy UNIQUE
    Scope scope = new ScopeBuilderImpl().discreteVariable(variable, variableIndex.getCardinality(variable)).buildScope();

    ConditionalDiscreteDistributionPrior prior = new ConditionalDiscreteDistributionPrior(scope, scope.subScope(), new UniformDirichletPriorInitializer());

    // TODO: don't make a complete query everytime but persist and update prior
    // TODO: could also use one query to build all distributions
    List<HistoricDecisionInstance> historicDecisionInstances = historyService
      .createHistoricDecisionInstanceQuery()
      .includeOutputs()
      .decisionDefinitionId(dmnModelId)
      .list();

    DmnModelInstance dmnModel = repositoryService.getDmnModelInstance(dmnModelId);
    Collection<Input> inputs = getInputs(dmnModel);

    for (HistoricDecisionInstance historicDecisionInstance : historicDecisionInstances) {
      String matchedRule = determineMatchedRule(historicDecisionInstance);

      Collection<InputEntry> inputEntries = getInputEntriesForRule(dmnModel, matchedRule);

      Iterator<Input> inputIt = inputs.iterator();
      Iterator<InputEntry> inputEntryIt = inputEntries.iterator();

      while (inputIt.hasNext() && inputEntryIt.hasNext()) {
        Input input = inputIt.next();
        InputEntry inputEntry = inputEntryIt.next();

        if (input.getLabel().equals(variable)) {
          String inputValue = inputEntry.getTextContent();
          int assignmentIndex = variableIndex.getIndex(variable, inputValue);
          prior.submitEvidence(new int[]{}, assignmentIndex);
        }
      }
    }

    return new PriorDistribution(variable, prior, variableIndex);
  }

  protected String determineMatchedRule(HistoricDecisionInstance historicDecisionInstance) {
    // assuming there is exactly one matching rule
    HistoricDecisionOutputInstance outputInstance = historicDecisionInstance.getOutputs().get(0);
    return outputInstance.getRuleId();
  }

  protected Collection<InputEntry> getInputEntriesForRule(DmnModelInstance dmnModel, String ruleId) {
    Rule rule = dmnModel.getModelElementById(ruleId);
    return rule.getInputEntries();
  }

  protected Collection<Input> getInputs(DmnModelInstance dmnModel) {
    // TODO: assuming there is exactly one decision table
    DecisionTable table = dmnModel.getModelElementsByType(DecisionTable.class).iterator().next();

    return table.getInputs();
  }

  public static class PriorDistribution implements Distribution {

    protected VariableIndex variableIndex;
    protected double[] values;
    protected String variable;

    public PriorDistribution(String variable, ConditionalDiscreteDistributionPrior prior, VariableIndex variableIndex) {
      this.values = prior.toCanonicalValueVector();
      this.variableIndex = variableIndex;
      this.variable = variable;
    }

    public double getProbability(String value) {
      int index = variableIndex.getIndex(variable, value);
      return values[index];
    }

    public Collection<String> getValues() {
      return variableIndex.getVariableValues(variable);
    }

  }

}
