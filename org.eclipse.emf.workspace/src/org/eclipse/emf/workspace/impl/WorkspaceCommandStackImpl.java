/**
 * <copyright>
 *
 * Copyright (c) 2005, 2006 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   IBM - Initial API and implementation
 *
 * </copyright>
 *
 * $Id: WorkspaceCommandStackImpl.java,v 1.6 2006/10/10 14:31:52 cdamus Exp $
 */
package org.eclipse.emf.workspace.impl;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.IOperationHistory;
import org.eclipse.core.commands.operations.IOperationHistoryListener;
import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.core.commands.operations.OperationHistoryEvent;
import org.eclipse.core.commands.operations.UndoContext;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.command.BasicCommandStack;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.transaction.ExceptionHandler;
import org.eclipse.emf.transaction.NotificationFilter;
import org.eclipse.emf.transaction.ResourceSetChangeEvent;
import org.eclipse.emf.transaction.ResourceSetListenerImpl;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.impl.EMFCommandTransaction;
import org.eclipse.emf.transaction.impl.InternalTransaction;
import org.eclipse.emf.transaction.impl.InternalTransactionalCommandStack;
import org.eclipse.emf.transaction.impl.InternalTransactionalEditingDomain;
import org.eclipse.emf.transaction.impl.TransactionalCommandStackImpl;
import org.eclipse.emf.transaction.impl.TriggerCommandTransaction;
import org.eclipse.emf.transaction.util.TriggerCommand;
import org.eclipse.emf.workspace.EMFCommandOperation;
import org.eclipse.emf.workspace.IWorkspaceCommandStack;
import org.eclipse.emf.workspace.ResourceUndoContext;
import org.eclipse.emf.workspace.WorkspaceEditingDomainFactory;
import org.eclipse.emf.workspace.internal.EMFWorkspacePlugin;
import org.eclipse.emf.workspace.internal.EMFWorkspaceStatusCodes;
import org.eclipse.emf.workspace.internal.Tracing;
import org.eclipse.emf.workspace.internal.l10n.Messages;

/**
 * Implementation of a transactional command stack that delegates
 * execution of commands to an {@link IOperationHistory}.
 * <p>
 * This is the command stack implementation used by editing domains created by
 * the {@link WorkspaceEditingDomainFactory}.
 * </p>
 * 
 * @author Christian W. Damus (cdamus)
 */
public class WorkspaceCommandStackImpl
		extends BasicCommandStack
		implements IWorkspaceCommandStack, InternalTransactionalCommandStack {
	
	private InternalTransactionalEditingDomain domain;
	private final IOperationHistory history;
	private DomainListener domainListener;
	
	private final IUndoContext defaultContext = new UndoContext();
	private IUndoContext savedContext = null;
	private Set affectedResources;
	
	private ExceptionHandler exceptionHandler;
	
	private IUndoableOperation mostRecentOperation;
	
	/**
	 * Initializes me with the operation history to which I delegate command
	 * execution.
	 * 
	 * @param history my operation history
	 */
	public WorkspaceCommandStackImpl(IOperationHistory history) {
		super();
		
		this.history = history;
		domainListener = new DomainListener();
	}

	// Documentation copied from the method specification
	public InternalTransactionalEditingDomain getDomain() {
		return domain;
	}

	// Documentation copied from the method specification
	public void setEditingDomain(InternalTransactionalEditingDomain domain) {
		if (this.domain != null) {
			this.domain.removeResourceSetListener(domainListener);
			history.removeOperationHistoryListener(domainListener);
		}
		
		this.domain = domain;
		
		if (domain != null) {
			history.addOperationHistoryListener(domainListener);
			domain.addResourceSetListener(domainListener);
		}
	}
	
	// Documentation copied from the method specification
	public final IOperationHistory getOperationHistory() {
		return history;
	}
	
	// Documentation copied from the method specification
	public final IUndoContext getDefaultUndoContext() {
		return defaultContext;
	}
	
	private final IUndoContext getSavedContext() {
		if (savedContext == null) {
			savedContext = new UndoContext();
		}
		return savedContext;
	}

	// Documentation copied from the method specification
	public void execute(Command command, Map options)
			throws InterruptedException, RollbackException {
		EMFCommandOperation oper = new EMFCommandOperation(getDomain(), command, options);
		
		// add the appropriate context
		oper.addContext(getDefaultUndoContext());
		
		try {
			IStatus status = history.execute(oper, new NullProgressMonitor(), null);
			
			if (status.getSeverity() >= IStatus.ERROR) {
				// the transaction must have rolled back if the status was
				//    error or worse
				RollbackException exc = new RollbackException(status);
				Tracing.throwing(WorkspaceCommandStackImpl.class,
						"execute", exc); //$NON-NLS-1$
				throw exc;
			}
			
			notifyListeners();
		} catch (ExecutionException e) {
			Tracing.catching(WorkspaceCommandStackImpl.class, "execute", e); //$NON-NLS-1$
			command.dispose();
			
			if (e.getCause() instanceof RollbackException) {
				// throw the rollback
				RollbackException exc = (RollbackException) e.getCause();
				Tracing.throwing(WorkspaceCommandStackImpl.class, "execute", exc); //$NON-NLS-1$
				throw exc;
			} else if (e.getCause() instanceof RuntimeException) {
				// throw the programming error
				RuntimeException exc = (RuntimeException) e.getCause();
				Tracing.throwing(WorkspaceCommandStackImpl.class, "execute", exc); //$NON-NLS-1$
				throw exc;
			} else {
				// log the problem.  We can't rethrow whatever it was
				handleError(e);
			}
		}
	}

	// Documentation copied from the method specification
	public void execute(Command command) {
		try {
			execute(command, null);
		} catch (InterruptedException e) {
			Tracing.catching(WorkspaceCommandStackImpl.class, "execute", e); //$NON-NLS-1$
			// just log it.  Note that the transaction is already rolled back,
			//    so handleError() will not find an active transaction
			handleError(e);
		} catch (RollbackException e) {
			Tracing.catching(WorkspaceCommandStackImpl.class, "execute", e); //$NON-NLS-1$
			// just log it.  Note that the transaction is already rolled back,
			//    so handleError() will not find an active transaction
			handleError(e);
		}
	}

	// Documentation copied from the method specification
	public void setExceptionHandler(ExceptionHandler handler) {
		this.exceptionHandler = handler;
	}
	
	// Documentation copied from the method specification
	public ExceptionHandler getExceptionHandler() {
		return exceptionHandler;
	}
	
	/**
	 * Extends the inherited method by first passing the exception along to
	 * the registered exception handler (if any).
	 */
	protected void handleError(Exception exception) {
		if (!isCancelException(exception)) {
			if (exceptionHandler != null) {
				try {
					exceptionHandler.handleException(exception);
				} catch (Exception e) {
					EMFWorkspacePlugin.INSTANCE.log(new Status(
							IStatus.WARNING,
							EMFWorkspacePlugin.getPluginId(),
							EMFWorkspaceStatusCodes.EXCEPTION_HANDLER_FAILED,
							Messages.exceptionHandlerFailed,
							e));
				}
			}
			
			super.handleError(exception); // super logs
		}
	}
	
	/**
	 * Does the specified exception indicate that the user canceled execution,
	 * undo, or redo of a command?
	 * 
	 * @param exception an exception
	 * @return <code>true</code> if it is an {@link OperationCanceledException}
	 *     or a {@link RollbackException} that was caused by operation cancel
	 */
	private boolean isCancelException(Throwable exception) {
		boolean result;
		
		if (exception instanceof OperationCanceledException) {
			result = true;
		} else if (exception instanceof RollbackException) {
			IStatus status = ((RollbackException) exception).getStatus();
			result = (status != null) &&
				((status.getSeverity() == IStatus.CANCEL)
					|| isCancelException(status.getException()));
		} else {
			result = false;
		}
		
		return result;
	}

	/**
	 * Queries whether we can undo my default undo context in my operation history.
	 */
	public boolean canUndo() {
		return getOperationHistory().canUndo(getDefaultUndoContext());
	}

	/**
	 * Undoes my default undo context in my operation history.
	 */
	public void undo() {
		try {
			getOperationHistory().undo(
					getDefaultUndoContext(),
					new NullProgressMonitor(),
					null);
		} catch (ExecutionException e) {
			Tracing.catching(WorkspaceCommandStackImpl.class, "undo", e); //$NON-NLS-1$
			
			// can't throw anything from this method
			handleError(e);
		}
	}

	/**
	 * Queries whether we can redo my default undo context in my operation history.
	 */
	public boolean canRedo() {
		return getOperationHistory().canRedo(getDefaultUndoContext());
	}

	/**
	 * Redoes my default undo context in my operation history.
	 */
	public void redo() {
		try {
			getOperationHistory().redo(
					getDefaultUndoContext(),
					new NullProgressMonitor(),
					null);
		} catch (ExecutionException e) {
			Tracing.catching(WorkspaceCommandStackImpl.class, "redo", e); //$NON-NLS-1$
			
			// can't throw anything from this method
			handleError(e);
		}
	}

	/**
	 * Disposes my default undo context in my operation history.
	 */
	public void flush() {
		getOperationHistory().dispose(
				getDefaultUndoContext(),
				true, true, true);
		
		if (savedContext != null) {
			getOperationHistory().dispose(
				getSavedContext(), 
				true, true, true);
			savedContext = null;
		}
	}
	
	/**
	 * Gets the command from the most recently executed, done, or redone
	 * operation.
	 */
	public Command getMostRecentCommand() {
		Command result = null;
		
		if (mostRecentOperation instanceof EMFCommandOperation) {
			result = ((EMFCommandOperation) mostRecentOperation).getCommand();
		}
		
		return result;
	}

	/**
	 * Gets the command from the top of the undo history, if any.
	 */
	public Command getUndoCommand() {
		Command result = null;
		
		IUndoableOperation topOperation = getOperationHistory().getUndoOperation(
				getDefaultUndoContext());
		
		if (topOperation instanceof EMFCommandOperation) {
			result = ((EMFCommandOperation) topOperation).getCommand();
		}
		
		return result;
	}
	
	/**
	 * Gets the command from the top of the redo history, if any.
	 */
	public Command getRedoCommand() {
		Command result = null;
		
		IUndoableOperation topOperation = getOperationHistory().getRedoOperation(
				getDefaultUndoContext());
		
		if (topOperation instanceof EMFCommandOperation) {
			result = ((EMFCommandOperation) topOperation).getCommand();
		}
		
		return result;
	}
	
	// Documentation copied from the method specification
	public EMFCommandTransaction createTransaction(Command command, Map options) throws InterruptedException {
		EMFCommandTransaction result;
		
		if (command instanceof TriggerCommand) {
			result = new TriggerCommandTransaction((TriggerCommand) command,
					getDomain(), options);
		} else {
			result = new EMFOperationTransaction(command, getDomain(), options);
		}
		
		result.start();
		
		return result;
	}

	// Documentation copied from the method specification
	public void executeTriggers(Command command, List triggers, Map options) throws InterruptedException, RollbackException {
		if (!triggers.isEmpty()) {
			TriggerCommand trigger = (command == null)
				? new TriggerCommand(triggers)
				: new TriggerCommand(command, triggers);
			
			InternalTransaction tx = createTransaction(trigger, options);
			
			try {
				trigger.execute();
				
				InternalTransaction parent = (InternalTransaction) tx.getParent();
				
				// shouldn't be null if we're executing triggers!
				if (parent != null) {
					parent.addTriggers(trigger);
				}
				
				// commit the transaction now
				tx.commit();
			} catch (RuntimeException e) {
				Tracing.catching(TransactionalCommandStackImpl.class, "executeTriggers", e); //$NON-NLS-1$
				
				IStatus status;
				if (e instanceof OperationCanceledException) {
					status = Status.CANCEL_STATUS;
				} else {
					status = new Status(
							IStatus.ERROR,
							EMFWorkspacePlugin.getPluginId(),
							EMFWorkspaceStatusCodes.PRECOMMIT_FAILED,
							Messages.precommitFailed,
							e);
				}
				RollbackException rbe = new RollbackException(status);
				Tracing.throwing(TransactionalCommandStackImpl.class, "executeTriggers", rbe); //$NON-NLS-1$
				throw rbe;
			} finally {
				if ((tx != null) && (tx.isActive())) {
					// roll back because an uncaught exception occurred
					rollback(tx);
				}
			}
		}
	}
	
	/**
	 * Ensures that the specified transaction is rolled back, first rolling
	 * back a nested transaction (if any).
	 * 
	 * @param tx a transaction to roll back
	 */
	void rollback(Transaction tx) {
		while (tx.isActive()) {
			Transaction active = domain.getActiveTransaction();
			
			active.rollback();
		}
	}
	
	// Documentation copied from the method specification
	public void dispose() {
		setEditingDomain(null);  // remove listeners
		domainListener = null;
		affectedResources = null;
		mostRecentOperation = null;
	}

	/**
	 * A listener on the editing domain and operation history that tracks
	 * which resources are changed by an operation and attaches the appropriate
	 * {@link ResourceUndoContext} to it when it completes.
	 *
	 * @author Christian W. Damus (cdamus)
	 */
	private class DomainListener extends ResourceSetListenerImpl implements IOperationHistoryListener {
		public void historyNotification(OperationHistoryEvent event) {
			final IUndoableOperation operation = event.getOperation();
			
			switch (event.getEventType()) {
			case OperationHistoryEvent.ABOUT_TO_EXECUTE:
				// set up a resource undo context in case we make EMF changes
				affectedResources = new java.util.HashSet();
				break;
			case OperationHistoryEvent.DONE:
				if ((affectedResources != null) && !affectedResources.isEmpty()) {
					// add my undo context to the operation that has completed, but
					//    only if the operation actually changed any of my resources
					//    (in case this history is shared with other domains)
					for (Iterator iter = affectedResources.iterator(); iter.hasNext();) {
						operation.addContext(new ResourceUndoContext(
								getDomain(),
								(Resource) iter.next()));
					}
				}
				
				affectedResources = null;
				
				if (operation.hasContext(getDefaultUndoContext())) {
					mostRecentOperation = operation;
				}
				break;
			case OperationHistoryEvent.OPERATION_NOT_OK:
				// just forget about the context because this operation failed
				affectedResources = null;
				break;
			case OperationHistoryEvent.UNDONE:
			case OperationHistoryEvent.REDONE:
				if (operation.hasContext(getDefaultUndoContext())) {
					mostRecentOperation = operation;
				}
				break;
			case OperationHistoryEvent.OPERATION_REMOVED:
				if (operation == mostRecentOperation) {
					mostRecentOperation = null;
				}
				break;
			}
		}
		
		public void resourceSetChanged(ResourceSetChangeEvent event) {
			if (affectedResources != null) {
				// there is an operation executing on our history that is affecting
				//    my editing domain.  Populate the resource undo context
				affectedResources.addAll(
						ResourceUndoContext.getAffectedResources(
								event.getNotifications()));
			}
			
			Set unloaded = getUnloadedResources(event.getNotifications());
			if (unloaded != null) {
				if (affectedResources != null) {
					// don't add these resources to the operation
					affectedResources.removeAll(unloaded);
				}
				
				for (Iterator iter = unloaded.iterator(); iter.hasNext();) {
					getOperationHistory().dispose(
							new ResourceUndoContext(
									getDomain(),
									(Resource) iter.next()),
							true, true, true);
				}
			}
		}
		
		/**
		 * Finds resources that have sent unload notifications.
		 * 
		 * @param notifications notifications received from a transaction
		 * @return a set of resources that the notifications indicate have been
		 *     unloaded, or <code>null</code> if none
		 */
		private Set getUnloadedResources(Collection notifications) {
			Set result = null;
			
			for (Iterator iter = notifications.iterator(); iter.hasNext();) {
				Notification next = (Notification) iter.next();
				
				if (NotificationFilter.RESOURCE_UNLOADED.matches(next)) {
					if (result == null) {
						result = new java.util.HashSet();
					}
					
					result.add(next.getNotifier());
				}
			}
			
			return result;
		}
		
		public boolean isPostcommitOnly() {
			// only interested in post-commit "resourceSetChanged" event
			return true;
		}
	}
	
	public boolean isSaveNeeded() {
		// We override the execute method and never call the super implementation
		//  so we have to implement the isSaveNeeded method ourselves.
		IUndoableOperation nextUndoableOperation = history.getUndoOperation(getDefaultUndoContext());
		
		if (nextUndoableOperation == null)
			return false;
		
		return savedContext != null ? !nextUndoableOperation.hasContext(getSavedContext()) : true;
	}
	
	public void saveIsDone() {
		// We override the execute method and never call the super implementation
		//  so we have to implement the saveIsDone method ourselves.
		
		if (savedContext != null) {
			// The save context is only stored on one operation. We must
			//  remove it from any other operation that may have contained it before.
			IUndoableOperation[] undoableOperations = history.getUndoHistory(getSavedContext());
			for (int i=0; i<undoableOperations.length; i++) {
				undoableOperations[i].removeContext(getSavedContext());
			}
			IUndoableOperation[] redoableOperations = history.getRedoHistory(getSavedContext());
			for (int i=0; i<redoableOperations.length; i++) {
				redoableOperations[i].removeContext(getSavedContext());
			}
		}
		
		IUndoableOperation nextUndoableOperation = history.getUndoOperation(getDefaultUndoContext());
		if (nextUndoableOperation == null) {
			return;
		}
		
		nextUndoableOperation.addContext(getSavedContext());
	}
}
