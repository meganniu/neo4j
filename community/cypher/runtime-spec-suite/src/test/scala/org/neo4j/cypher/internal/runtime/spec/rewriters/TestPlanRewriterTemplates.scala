/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.runtime.spec.rewriters

import org.neo4j.cypher.internal.logical.plans.AssertSameNode
import org.neo4j.cypher.internal.logical.plans.Input
import org.neo4j.cypher.internal.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.logical.plans.ProduceResult
import org.neo4j.cypher.internal.runtime.spec.rewriters.TestPlanCombinationRewriterConfig.PlanRewriterStepConfig
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.RewriterWithParent
import org.neo4j.cypher.internal.util.bottomUpWithParent
import org.neo4j.cypher.internal.util.topDown

import scala.util.Random

object TestPlanRewriterTemplates {

  // --------------------------------------------------------------------------
  // Rewriter templates
  // --------------------------------------------------------------------------
  def everywhere(
    ctx: PlanRewriterContext,
    config: PlanRewriterStepConfig,
    rewritePlan: LogicalPlan => LogicalPlan
  ): Rewriter = {
    bottomUpWithParent(
      RewriterWithParent.lift {
        case (pr: ProduceResult, _) =>
          pr
        case (p: LogicalPlan, parent: Option[LogicalPlan])
          if isParentOkToInterject(parent) && randomShouldApply(config) =>
          rewritePlan(p)
      },
      onlyRewriteLogicalPlansStopper
    )
  }

  def onTop(
    ctx: PlanRewriterContext,
    config: PlanRewriterStepConfig,
    rewritePlan: LogicalPlan => LogicalPlan
  ): Rewriter = topDown(
    Rewriter.lift {
      case ProduceResult(source, _) if isLeftmostLeafOkToMove(source) && randomShouldApply(config) =>
        rewritePlan(source)
    },
    onlyRewriteLogicalPlansStopper
  )

  // --------------------------------------------------------------------------
  // Conditions
  // --------------------------------------------------------------------------
  def randomShouldApply(stepConfig: PlanRewriterStepConfig): Boolean = {
    stepConfig.weight match {
      case 0.0 =>
        false
      case 1.0 =>
        true
      case w =>
        Random.nextDouble() < w
    }
  }

  def isLeftmostLeafOkToMove(plan: LogicalPlan): Boolean = {
    plan.leftmostLeaf match {
      case _: Input =>
        false

      case _ =>
        true
    }
  }

  def isParentOkToInterject(parent: Option[LogicalPlan]): Boolean = {
    parent match {
      case Some(_: AssertSameNode) =>
        // AssertSameNode is only supported by rewriter in pipelined, and it relies on assumptions about the possible plans,
        // so we cannot insert a plan between it and its children
        false
      case _ =>
        true
    }
  }

  // --------------------------------------------------------------------------
  // Stoppers
  // --------------------------------------------------------------------------
  def onlyRewriteLogicalPlansStopper(a: AnyRef): Boolean = a match {
    // Only rewrite logical plans
    case _: LogicalPlan => false
    case _              => true
  }
}
