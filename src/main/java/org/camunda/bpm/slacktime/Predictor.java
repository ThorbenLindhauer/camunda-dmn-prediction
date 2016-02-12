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

import java.util.Collection;

import com.github.thorbenlindhauer.inference.DiscreteModelInferencer;
import com.github.thorbenlindhauer.inference.VariableEliminationInferencer;
import com.github.thorbenlindhauer.inference.variableelimination.MinFillEliminationStrategy;
import com.github.thorbenlindhauer.variable.Scope;

/**
 * @author Thorben Lindhauer
 *
 */
public class Predictor {

  protected CanonicalDmnModel transformedModel;

  protected DiscreteModelInferencer inferencer;

  public Predictor(CanonicalDmnModel transformedModel) {
    this.transformedModel = transformedModel;
    this.inferencer = initializeInferencer(transformedModel);
  }

  protected static DiscreteModelInferencer initializeInferencer(CanonicalDmnModel model) {
    // TODO could be configurable at some point in time
    return new VariableEliminationInferencer(model.graphicalModel, new MinFillEliminationStrategy());

  }

  public Distribution getPosterior(final String variable, Evidence evidence) {
    final Scope evidenceScope = transformedModel.toScope(evidence);
    final int[] evidenceAssignment = transformedModel.toCanonicalAssignment(evidence);
    final Scope projectionScope = transformedModel.graphicalModel.getScope().subScope(variable);

    return new Distribution() {

      public Collection<String> getValues() {
        return transformedModel.variableIndex.getVariableValues(variable);
      }

      public double getProbability(String value) {
        int assignment = transformedModel.variableIndex.getIndex(variable, value);
        return inferencer.jointProbabilityConditionedOn(
            projectionScope,
            new int[]{assignment},
            evidenceScope,
            evidenceAssignment);
      }
    };
  }
}
