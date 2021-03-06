/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
  *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.ejb3.async.impl.util.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jboss.ejb3.async.spi.AsyncCancellableContext;
import org.jboss.ejb3.async.spi.AsyncInvocationId;
import org.jboss.logging.Logger;

/**
 * Client view of an EJB 3.1 Asynchronous invocation's return
 * value 
 * 
 * <br /><br />
 * 
 * Required to unwrap the javax.ejb.AsyncResult<V> (or any j.u.c.Future)
 * that has been given as a return value by the bean provider
 *
 * @author <a href="mailto:andrew.rubinger@jboss.org">ALR</a>
 * @version $Revision: $
 */
public class AsynchronousClientFuture<V> extends FutureTask<V> implements Future<V>
{
   // --------------------------------------------------------------------------------||
   // Class Members ------------------------------------------------------------------||
   // --------------------------------------------------------------------------------||

   private static final long serialVersionUID = 1L;

   private static final Logger log = Logger.getLogger(AsynchronousClientFuture.class);

   // --------------------------------------------------------------------------------||
   // Instance Members ---------------------------------------------------------------||
   // --------------------------------------------------------------------------------||

   /**
    * ID of this invocation, used in issuing calls to {@link Future#cancel(boolean)}
    */
   private final AsyncInvocationId id;

   /**
    * View of the container
    */
   private final AsyncCancellableContext container;

   // --------------------------------------------------------------------------------||
   // Constructor --------------------------------------------------------------------||
   // --------------------------------------------------------------------------------||

   /*
    * Delegate constructors back up to the super implementation
    */

   public AsynchronousClientFuture(final Callable<V> callable, final AsyncInvocationId id,
         final AsyncCancellableContext container)
   {
      super(callable);
      assert id != null : "Async invocation ID must be specified";
      this.id = id;
      assert container != null : "Container must be supplied";
      this.container = container;
   }

   public AsynchronousClientFuture(final Runnable runnable, final V result, final AsyncInvocationId id,
         final AsyncCancellableContext container)
   {
      super(runnable, result);
      assert id != null : "Async invocation ID must be specified";
      this.id = id;
      assert container != null : "Container must be supplied";
      this.container = container;
   }

   // --------------------------------------------------------------------------------||
   // Required Implementations -------------------------------------------------------||
   // --------------------------------------------------------------------------------||

   /**
    * {@inheritDoc}
    * @see java.util.concurrent.Future#get()
    */
   public V get() throws InterruptedException, ExecutionException
   {
      // Log
      if (log.isTraceEnabled())
      {
         log.trace("Blocking request to get()");
      }

      // Get the result specified by the bean provider
      final Object returnValueFromBeanProvider = super.get();
      final V wrappedValue = this.getWrappedFuture(returnValueFromBeanProvider);

      // Return
      return wrappedValue;
   }

   /**
    * {@inheritDoc}
    * @see java.util.concurrent.Future#get(long, java.util.concurrent.TimeUnit)
    */
   public V get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException,
         TimeoutException
   {
      // Log
      if (log.isTraceEnabled())
      {
         log.trace("Request to get() with timeout " + timeout + " (" + unit + ")");
      }

      // Get the result specified by the bean provider
      final Object returnValueFromBeanProvider = super.get(timeout, unit);
      final V wrappedValue = this.getWrappedFuture(returnValueFromBeanProvider);

      // Return
      return wrappedValue;
   }

   /**
    * {@inheritDoc}
    * @see java.util.concurrent.FutureTask#cancel(boolean)
    */
   @Override
   public boolean cancel(final boolean mayInterruptIfRunning)
   {
      // First see if already done
      if (this.isDone())
      {
         // Cannot cancel a completed operation
         return false;
      }

      // If we can't cancel per normal, send along to the server to cancel
      boolean returnValue = super.cancel(mayInterruptIfRunning);
      if ((!returnValue) && mayInterruptIfRunning)
      {
         // Send a flag to the server to cancel
         container.cancel(id);
         returnValue = true;
      }

      // Return
      return returnValue;
   }

   // --------------------------------------------------------------------------------||
   // Internal Helper Methods --------------------------------------------------------||
   // --------------------------------------------------------------------------------||

   /**
    * Unwraps the AsyncFuture (or any j.u.c.Future) result given by the bean provider
    * and returns the real return value
    */
   @SuppressWarnings("unchecked")
   private V getWrappedFuture(final Object returnValueFromBeanProvider) throws InterruptedException, ExecutionException
   {
      // Allow nulls to pass through (return type of void)
      if (returnValueFromBeanProvider == null)
      {
         return null;
      }

      // Ensure it's in expected form
      if (!(returnValueFromBeanProvider instanceof Future))
      {
         throw new RuntimeException("Bean provider has not specified a return value of type " + Future.class.getName()
               + ", was instead: " + returnValueFromBeanProvider);
      }

      // Get the underlying result
      final Future<V> result = (Future<V>) returnValueFromBeanProvider;
      final V unwrappedReturnValue = result.get(); // Not blocking, as AsyncResult is not a blocking implementation

      // Return
      return unwrappedReturnValue;
   }

}
