/*
 * Copyright 2009-2010 by The Regents of the University of California
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;

import edu.uci.ics.hyracks.algebricks.common.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.common.utils.Pair;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.IOptimizationContext;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.LogicalOperatorTag;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.AbstractLogicalExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.AbstractLogicalOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.OrderOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.OrderOperator.IOrder;
import edu.uci.ics.hyracks.algebricks.core.rewriter.base.IAlgebraicRewriteRule;

/**
 * If there is any ordering property before the subplan operator, the ordering should
 * be kept after the subplan.
 * This rule adds a redundant order operator after those cases, to guarantee the correctness.
 * 
 * @author yingyib
 */
public class EnforceOrderByAfterSubplan implements IAlgebraicRewriteRule {
    /** a set of order-breaking operators */
    private final Set<LogicalOperatorTag> orderBreakingOps = new HashSet<LogicalOperatorTag>();

    public EnforceOrderByAfterSubplan() {
        /** add operators that break the ordering */
        orderBreakingOps.add(LogicalOperatorTag.INNERJOIN);
        orderBreakingOps.add(LogicalOperatorTag.LEFTOUTERJOIN);
        orderBreakingOps.add(LogicalOperatorTag.UNIONALL);
    }

    @Override
    public boolean rewritePre(Mutable<ILogicalOperator> opRef, IOptimizationContext context) throws AlgebricksException {
        return false;
    }

    @Override
    public boolean rewritePost(Mutable<ILogicalOperator> opRef, IOptimizationContext context)
            throws AlgebricksException {
        AbstractLogicalOperator op1 = (AbstractLogicalOperator) opRef.getValue();
        if (context.checkIfInDontApplySet(this, op1)) {
            return false;
        }
        List<Mutable<ILogicalOperator>> inputs = op1.getInputs();
        context.addToDontApplySet(this, op1);
        if (op1.getOperatorTag() == LogicalOperatorTag.ORDER || inputs == null) {
            /**
             * does not apply if
             * 1. there is yet-another order operator on-top-of the subplan, because the downstream order operator's ordering will be broken anyway
             * 2. the input operator(s) is null
             */
            return false;
        }
        boolean changed = false;
        for (int i = 0; i < inputs.size(); i++) {
            Mutable<ILogicalOperator> inputOpRef = inputs.get(i);
            AbstractLogicalOperator op = (AbstractLogicalOperator) inputOpRef.getValue();
            context.addToDontApplySet(this, op);
            if (op.getOperatorTag() != LogicalOperatorTag.SUBPLAN) {
                continue;
            }

            /**
             * check the order operators whose ordering is not broken before the subplan operator, and then
             * duplicate them on-top-of the subplan operator
             */
            boolean foundTarget = true;
            AbstractLogicalOperator child = (AbstractLogicalOperator) op.getInputs().get(0).getValue();
            while (child.getOperatorTag() != LogicalOperatorTag.ORDER) {
                context.addToDontApplySet(this, child);
                if (orderBreakingOps.contains(child.getOperatorTag())) {
                    foundTarget = false;
                    break;
                }
                List<Mutable<ILogicalOperator>> childInputs = child.getInputs();
                if (childInputs == null || childInputs.size() > 2 || childInputs.size() < 1) {
                    foundTarget = false;
                    break;
                } else {
                    child = (AbstractLogicalOperator) childInputs.get(0).getValue();
                }
            }
            /** the target order-by operator has not been found. */
            if (!foundTarget) {
                return false;
            }

            /** duplicate the order-by operator and insert on-top-of the subplan operator */
            context.addToDontApplySet(this, child);
            OrderOperator sourceOrderOp = (OrderOperator) child;
            List<Pair<IOrder, Mutable<ILogicalExpression>>> orderExprs = deepCopyOrderAndExpression(sourceOrderOp
                    .getOrderExpressions());
            OrderOperator newOrderOp = new OrderOperator(orderExprs);
            context.addToDontApplySet(this, newOrderOp);
            inputs.set(i, new MutableObject<ILogicalOperator>(newOrderOp));
            newOrderOp.getInputs().add(inputOpRef);
            context.computeAndSetTypeEnvironmentForOperator(newOrderOp);
            changed = true;
        }
        return changed;
    }

    private Mutable<ILogicalExpression> deepCopyExpressionRef(Mutable<ILogicalExpression> oldExpr) {
        return new MutableObject<ILogicalExpression>(((AbstractLogicalExpression) oldExpr.getValue()).cloneExpression());
    }

    private List<Pair<IOrder, Mutable<ILogicalExpression>>> deepCopyOrderAndExpression(
            List<Pair<IOrder, Mutable<ILogicalExpression>>> ordersAndExprs) {
        List<Pair<IOrder, Mutable<ILogicalExpression>>> newOrdersAndExprs = new ArrayList<Pair<IOrder, Mutable<ILogicalExpression>>>();
        for (Pair<IOrder, Mutable<ILogicalExpression>> pair : ordersAndExprs)
            newOrdersAndExprs.add(new Pair<IOrder, Mutable<ILogicalExpression>>(pair.first,
                    deepCopyExpressionRef(pair.second)));
        return newOrdersAndExprs;
    }
}
