/*

 * Copyright 2009-2013 by The Regents of the University of California

 * Licensed under the Apache License, Version 2.0 (the "License");

 * you may not use this file except in compliance with the License.

 * you may obtain a copy of the License from

 * 

 *     http://www.apache.org/licenses/LICENSE-2.0

 * 

 * Unless required by applicable law or agreed to in writing, software

 * distributed under the License is distributed on an "AS IS" BASIS,

 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.

 * See the License for the specific language governing permissions and

 * limitations under the License.

 */

package edu.uci.ics.hyracks.algebricks.rewriter.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;

import edu.uci.ics.hyracks.algebricks.common.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.common.utils.ListSet;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalPlan;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.IOptimizationContext;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.LogicalOperatorTag;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.LogicalVariable;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.AbstractLogicalOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.GroupByOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.NestedTupleSourceOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.SubplanOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.visitors.VariableUtilities;
import edu.uci.ics.hyracks.algebricks.core.algebra.util.OperatorManipulationUtil;
import edu.uci.ics.hyracks.algebricks.core.rewriter.base.IAlgebraicRewriteRule;

/**
 * This rule pushes an array of subplans on top of a group-by into the
 * nested plan of the group-by.
 * 
 * @author yingyib
 */

public class PushSubplanIntoGroupByRule implements IAlgebraicRewriteRule {
    @Override
    public boolean rewritePost(Mutable<ILogicalOperator> opRef, IOptimizationContext context)
            throws AlgebricksException {
        return false;
    }

    @Override
    public boolean rewritePre(Mutable<ILogicalOperator> opRef, IOptimizationContext context) throws AlgebricksException {
        ILogicalOperator parentOperator = opRef.getValue();
        if (context.checkIfInDontApplySet(this, parentOperator)) {
            return false;
        }
        context.addToDontApplySet(this, parentOperator);
        if (parentOperator.getInputs().size() <= 0) {
            return false;
        }
        boolean changed = false;
        for (Mutable<ILogicalOperator> ref : parentOperator.getInputs()) {
            AbstractLogicalOperator op = (AbstractLogicalOperator) ref.getValue();
            /** Only processes subplan operator. */
            List<SubplanOperator> subplans = new ArrayList<SubplanOperator>();
            if (op.getOperatorTag() == LogicalOperatorTag.SUBPLAN) {
                while (op.getOperatorTag() == LogicalOperatorTag.SUBPLAN) {
                    SubplanOperator currentSubplan = (SubplanOperator) op;
                    subplans.add(currentSubplan);
                    op = (AbstractLogicalOperator) op.getInputs().get(0).getValue();
                }
                /** Only processes the case a group-by operator is the input of the subplan operators. */
                if (op.getOperatorTag() == LogicalOperatorTag.GROUP) {
                    GroupByOperator gby = (GroupByOperator) op;
                    List<ILogicalPlan> newGbyNestedPlans = new ArrayList<ILogicalPlan>();
                    for (SubplanOperator subplan : subplans) {
                        List<ILogicalPlan> subplanNestedPlans = subplan.getNestedPlans();
                        List<ILogicalPlan> gbyNestedPlans = gby.getNestedPlans();
                        List<ILogicalPlan> subplanNestedPlansToRemove = new ArrayList<ILogicalPlan>();
                        for (ILogicalPlan subplanNestedPlan : subplanNestedPlans) {
                            List<Mutable<ILogicalOperator>> rootOpRefs = subplanNestedPlan.getRoots();
                            List<Mutable<ILogicalOperator>> rootOpRefsToRemove = new ArrayList<Mutable<ILogicalOperator>>();
                            for (Mutable<ILogicalOperator> rootOpRef : rootOpRefs) {
                                /** Gets free variables in the root operator of a nested plan and its descent. */
                                Set<LogicalVariable> freeVars = new ListSet<LogicalVariable>();
                                VariableUtilities.getUsedVariablesInDescendantsAndSelf(rootOpRef.getValue(), freeVars);
                                Set<LogicalVariable> producedVars = new ListSet<LogicalVariable>();
                                VariableUtilities.getProducedVariablesInDescendantsAndSelf(rootOpRef.getValue(),
                                        producedVars);
                                freeVars.removeAll(producedVars);
                                /** * Checks whether the above freeVars are all contained in live variables * of one nested plan inside the group-by operator. * If yes, then the subplan can be pushed into the nested plan of the group-by. */
                                for (ILogicalPlan gbyNestedPlanOriginal : gbyNestedPlans) {
                                    ILogicalPlan gbyNestedPlan = OperatorManipulationUtil.deepCopy(
                                            gbyNestedPlanOriginal, context);
                                    List<Mutable<ILogicalOperator>> gbyRootOpRefs = gbyNestedPlan.getRoots();
                                    for (Mutable<ILogicalOperator> gbyRootOpRef : gbyRootOpRefs) {
                                        Set<LogicalVariable> liveVars = new ListSet<LogicalVariable>();
                                        VariableUtilities.getLiveVariables(gbyRootOpRef.getValue(), liveVars);
                                        if (liveVars.containsAll(freeVars)) {
                                            /** Does the actual push. */
                                            Mutable<ILogicalOperator> ntsRef = downToNts(rootOpRef);
                                            ntsRef.setValue(gbyRootOpRef.getValue());
                                            gbyRootOpRef.setValue(rootOpRef.getValue());
                                            rootOpRefsToRemove.add(rootOpRef);
                                            Mutable<ILogicalOperator> oldGbyNtsRef = downToNts(gbyRootOpRef);
                                            NestedTupleSourceOperator nts = (NestedTupleSourceOperator) oldGbyNtsRef
                                                    .getValue();
                                            nts.setDataSourceReference(new MutableObject<ILogicalOperator>(gby));
                                            newGbyNestedPlans.add(gbyNestedPlan);
                                            changed = true;
                                        }
                                    }
                                }
                            }
                            rootOpRefs.removeAll(rootOpRefsToRemove);
                            if (rootOpRefs.size() == 0) {
                                subplanNestedPlansToRemove.add(subplanNestedPlan);
                            }
                        }
                        subplanNestedPlans.removeAll(subplanNestedPlansToRemove);
                    }
                    if (changed) {
                        ref.setValue(gby);
                        gby.getNestedPlans().clear();
                        gby.getNestedPlans().addAll(newGbyNestedPlans);
                    }
                }
            }
        }
        return changed;
    }

    private Mutable<ILogicalOperator> downToNts(Mutable<ILogicalOperator> opRef) {
        Mutable<ILogicalOperator> currentOpRef = opRef;
        while (currentOpRef.getValue().getInputs().size() > 0) {
            currentOpRef = currentOpRef.getValue().getInputs().get(0);
        }
        return currentOpRef;
    }

}