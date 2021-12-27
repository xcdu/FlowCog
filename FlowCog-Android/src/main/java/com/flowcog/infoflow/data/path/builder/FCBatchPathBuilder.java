package com.flowcog.infoflow.data.path.builder;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import soot.jimple.infoflow.InfoflowManager;
import soot.jimple.infoflow.data.AbstractionAtSink;
import soot.jimple.infoflow.data.pathBuilders.AbstractAbstractionPathBuilder;
import soot.jimple.infoflow.data.pathBuilders.IAbstractionPathBuilder;
import soot.jimple.infoflow.memory.ISolverTerminationReason;
import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.solver.executors.InterruptableExecutor;

/**
 * Path builder that forwards all its requests to another path builder in
 * batches. This builder waits for each batch to complete before submitting
 * another batch. Use this path builder to reduce the memory consumption of the
 * path building process by keeping less paths in memory at the same time.
 *
 * @author Steven Arzt
 *
 */
public class FCBatchPathBuilder extends AbstractAbstractionPathBuilder {

  private final IAbstractionPathBuilder innerBuilder;
  private int batchSize = 5;
  private ISolverTerminationReason terminationReason = null;
  private boolean disableInnerBuilderReset = false;

  public FCBatchPathBuilder(InfoflowManager manager, IAbstractionPathBuilder innerBuilder) {
    super(manager);
    this.innerBuilder = innerBuilder;
  }

  @Override
  public void computeTaintPaths(Set<AbstractionAtSink> res) {
    Set<AbstractionAtSink> batch = new HashSet<>();
    Iterator<AbstractionAtSink> resIt = res.iterator();
    int batchId = 1;
    int processedBatch = 0;
    while (resIt.hasNext()) {
      // Build the next batch
      while (batch.size() < this.batchSize && resIt.hasNext())
        batch.add(resIt.next());
      if(!innerBuilder.isKilled()) {
        logger.info(
            String.format("Running path reconstruction batch %d with %d elements", batchId++,
                batch.size()));
        processedBatch++;
      } else {
        logger.debug(String.format("Inner Builder is killed, batch %d is skipped", batchId++));
      }

      // Run the next batch
      if(!disableInnerBuilderReset) {
        innerBuilder.reset();
      }
      innerBuilder.computeTaintPaths(batch);

      // Save the termination reason
      if (this.terminationReason == null)
        this.terminationReason = innerBuilder.getTerminationReason();
      else
        this.terminationReason = this.terminationReason.combine(innerBuilder.getTerminationReason());

      // Wait for the batch to complete
      if (innerBuilder instanceof FCConcurrentAbstractionPathBuilder) {
        FCConcurrentAbstractionPathBuilder fcConcurrentBuilder = (FCConcurrentAbstractionPathBuilder) innerBuilder;
        final InterruptableExecutor resultExecutor = getConcurrentBuilderExecutor(fcConcurrentBuilder);
        try {
          resultExecutor.awaitCompletion();
        } catch (InterruptedException e) {
          logger.error("Could not wait for executor termination", e);
        }
        resultExecutor.reset();
      }

      // Prepare for the next batch
      batch.clear();
    }
    logger.debug(String.format("%d/%d batches process in path reconstruction", processedBatch,
        batchId - 1));
  }

  @Override
  public InfoflowResults getResults() {
    return innerBuilder.getResults();
  }

  @Override
  public void runIncrementalPathCompuation() {
    innerBuilder.runIncrementalPathCompuation();
  }

  @Override
  public void forceTerminate(ISolverTerminationReason reason) {
    innerBuilder.forceTerminate(reason);
    logger.debug("innerBuilder {} terminated", innerBuilder.toString());
  }

  @Override
  public boolean isTerminated() {
    return innerBuilder.isTerminated();
  }

  @Override
  public boolean isKilled() {
    return innerBuilder.isKilled();
  }

  @Override
  public ISolverTerminationReason getTerminationReason() {
    return terminationReason;
  }

  @Override
  public void reset() {
    innerBuilder.reset();
  }

  @Override
  public void addStatusListener(IMemoryBoundedSolverStatusNotification listener) {
    innerBuilder.addStatusListener(listener);
  }

  /**
   * Sets the number of paths that shall be part of one batch, i.e., that shall be
   * forwarded to the inner path builder at the same time
   *
   * @param batchSize
   *            The number of paths in one batch
   */
  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  /**
   * Force to disable the reset function of innerBuilder to avoid unexpected processing
   * after result timeout
   * @param disableInnerBuilderReset
   */
  public void setDisableInnerBuilderReset(boolean disableInnerBuilderReset) {
    this.disableInnerBuilderReset = disableInnerBuilderReset;
  }

  private InterruptableExecutor getConcurrentBuilderExecutor(
      FCConcurrentAbstractionPathBuilder pathBuilder){
    for (Class<?> c = pathBuilder.getClass(); c != null; c = c.getSuperclass()) {
      try {
        final Field field = c.getDeclaredField("executor");
        field.setAccessible(true);
        return (InterruptableExecutor) field.get(pathBuilder);
      } catch (NoSuchFieldException e) {
        // Try parent
      } catch (Exception e) {
        logger.error(e.getMessage());
      }
    }
    return null;
  }

}
