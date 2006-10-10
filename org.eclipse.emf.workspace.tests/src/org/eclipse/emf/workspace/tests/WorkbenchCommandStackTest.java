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
 * $Id: WorkbenchCommandStackTest.java,v 1.6 2006/10/10 14:31:44 cdamus Exp $
 */
package org.eclipse.emf.workspace.tests;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.AbstractOperation;
import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.core.commands.operations.OperationHistoryFactory;
import org.eclipse.core.commands.operations.UndoContext;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.command.BasicCommandStack;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.edit.command.SetCommand;
import org.eclipse.emf.examples.extlibrary.EXTLibraryFactory;
import org.eclipse.emf.examples.extlibrary.EXTLibraryPackage;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.ResourceSetChangeEvent;
import org.eclipse.emf.transaction.ResourceSetListener;
import org.eclipse.emf.transaction.ResourceSetListenerImpl;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.TriggerListener;
import org.eclipse.emf.workspace.AbstractEMFOperation;
import org.eclipse.emf.workspace.EMFCommandOperation;
import org.eclipse.emf.workspace.EMFOperationCommand;
import org.eclipse.emf.workspace.IWorkspaceCommandStack;
import org.eclipse.emf.workspace.ResourceUndoContext;
import org.eclipse.emf.workspace.WorkspaceEditingDomainFactory;
import org.eclipse.emf.workspace.impl.WorkspaceCommandStackImpl;
import org.eclipse.emf.workspace.internal.EMFWorkspacePlugin;
import org.eclipse.emf.workspace.tests.fixtures.LogCapture;
import org.eclipse.emf.workspace.tests.fixtures.NullCommand;


/**
 * Tests the {@link WorkbenchCommandStack} class.
 *
 * @author Christian W. Damus (cdamus)
 */
public class WorkbenchCommandStackTest extends AbstractTest {

	private IUndoContext defaultContext;
	
	public WorkbenchCommandStackTest(String name) {
		super(name);
	}
	
	public static Test suite() {
		return new TestSuite(WorkbenchCommandStackTest.class, "Command Stack Tests"); //$NON-NLS-1$
	}
	
	/**
	 * Tests basic execution.
	 */
	public void test_execute() {
		Command cmd = new NullCommand();
		
		getCommandStack().execute(cmd);
		
		IUndoableOperation[] operations = history.getUndoHistory(defaultContext);
		
		assertNotNull(operations);
		assertEquals(1, operations.length);
		
		IUndoableOperation operation = operations[0];
		
		assertTrue(operation instanceof EMFCommandOperation);
		assertSame(cmd, ((EMFCommandOperation) operation).getCommand());
	}
	
	/**
	 * Tests undo/redo support.
	 */
	public void test_undo_redo() {
		Command cmd = new NullCommand();
		
		getCommandStack().execute(cmd);
		
		Command undo = getCommandStack().getUndoCommand();
		
		assertSame(cmd, undo);
		
		Command redo = getCommandStack().getRedoCommand();
		
		assertNull(redo);
		
		getCommandStack().undo();
		
		undo = getCommandStack().getUndoCommand();
		
		assertNull(undo);
		
		redo = getCommandStack().getRedoCommand();
		
		assertSame(cmd, redo);
		
		getCommandStack().redo();
		
		assertSame(cmd, getCommandStack().getUndoCommand());
	}
	
	/**
	 * Tests most-recent-command support.
	 */
	public void test_mostRecentCommand() {
		Command cmd = new NullCommand();
		
		// execute some other command
		getCommandStack().execute(new NullCommand());
		
		getCommandStack().execute(cmd);
		
		assertSame(cmd, getCommandStack().getMostRecentCommand());
		
		// execute some other command
		getCommandStack().execute(new NullCommand());
		
		assertNotSame(cmd, getCommandStack().getMostRecentCommand());
		
		getCommandStack().undo();
		
		assertNotSame(cmd, getCommandStack().getMostRecentCommand());
		
		getCommandStack().undo();
		
		assertSame(cmd, getCommandStack().getMostRecentCommand());
		
		getCommandStack().undo();
		
		getCommandStack().redo();
		
		assertNotSame(cmd, getCommandStack().getMostRecentCommand());
		
		getCommandStack().redo();
		
		assertSame(cmd, getCommandStack().getMostRecentCommand());
	}
	
	/**
	 * Tests flush support.
	 */
	public void test_flush() {
		getCommandStack().execute(new NullCommand());
		getCommandStack().execute(new NullCommand());
		
		IUndoableOperation[] operations = history.getUndoHistory(defaultContext);
		
		assertNotNull(operations);
		assertEquals(2, operations.length);
		
		getCommandStack().flush();
		
		operations = history.getUndoHistory(defaultContext);
		
		assertNotNull(operations);
		assertEquals(0, operations.length);
	}
	
	public void test_flushingOnResourceUnload() {
		Command cmd = new SetCommand(
				domain,
				root,
				EXTLibraryPackage.eINSTANCE.getLibrary_Name(),
				"foo"); //$NON-NLS-1$
		
		getCommandStack().execute(cmd);
		
		IUndoContext resctx = new ResourceUndoContext(domain, testResource);
		IUndoableOperation[] operations = history.getUndoHistory(resctx);
		
		assertNotNull(operations);
		assertEquals(1, operations.length);
		
		IUndoableOperation operation = operations[0];
		
		IUndoContext[] contexts = operation.getContexts();
		
		assertEquals(2, contexts.length);
		
		assertTrue((resctx.matches(contexts[0]) && defaultContext.matches(contexts[1]))
				|| (resctx.matches(contexts[1]) && defaultContext.matches(contexts[0])));
		
		// unload the resource (no transaction required)
		testResource.unload();

		// resource context was flushed
		operations = history.getUndoHistory(resctx);
		
		assertNotNull(operations);
		assertEquals(0, operations.length);
	}
	
	public void testUndoContextPropagationFromTriggerListeners() {
		final TransactionalEditingDomain domain = WorkspaceEditingDomainFactory.INSTANCE.createEditingDomain();
		final IUndoContext undoContext = new UndoContext();
		
		domain.addResourceSetListener(new ResourceSetListenerImpl() {
			public boolean isPrecommitOnly() {
				return true;
			}
			
			public Command transactionAboutToCommit(ResourceSetChangeEvent event)
				throws RollbackException {
				
				IUndoableOperation op = new AbstractOperation("") { //$NON-NLS-1$
					public IStatus execute(IProgressMonitor monitor, IAdaptable info)
						throws ExecutionException {
						return Status.OK_STATUS;
					}
	
					public IStatus redo(IProgressMonitor monitor, IAdaptable info)
						throws ExecutionException {
						return Status.OK_STATUS;
					}
	
					public IStatus undo(IProgressMonitor monitor, IAdaptable info)
						throws ExecutionException {
						return Status.OK_STATUS;
					}
				};
				
				op.addContext(undoContext);
				
				return new EMFOperationCommand(domain, op);
			}
		});
		
		final Resource r = domain.getResourceSet().createResource(URI.createURI("file://foo.xml")); //$NON-NLS-1$
		
		AbstractEMFOperation op = new AbstractEMFOperation(domain, "") { //$NON-NLS-1$
			protected IStatus doExecute(IProgressMonitor monitor, IAdaptable info)
				throws ExecutionException {
				
				r.getContents().add(EXTLibraryFactory.eINSTANCE.createLibrary());
				
				return Status.OK_STATUS;
			}
		};
		
		assertTrue(op.getContexts().length == 0);
		
		// Try executing the operation manually
		try {
			op.execute(new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			e.printStackTrace();
			fail();
		}
		
		assertNotNull(op.getContexts());
		assertTrue(op.getContexts().length > 0);
		assertTrue(op.getContexts()[0] == undoContext);
		
		op.removeContext(undoContext);
		
		try {
			OperationHistoryFactory.getOperationHistory().execute(op, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			e.printStackTrace();
			fail();
		}
		
		assertNotNull(op.getContexts());
		assertTrue(op.getContexts().length > 0);
		assertTrue(op.getContexts()[0] == undoContext);
	}
	
	public void testSaveIsDoneAPIs() {
		TransactionalEditingDomain domain = WorkspaceEditingDomainFactory.INSTANCE.createEditingDomain();
		final Resource r = domain.getResourceSet().createResource(URI.createURI("file://foo.xml")); //$NON-NLS-1$
		
		Command op = new RecordingCommand(domain) {

			protected void doExecute() {
				r.getContents().add(EXTLibraryFactory.eINSTANCE.createLibrary());
				
			}
		};
		
		BasicCommandStack stack = (BasicCommandStack)domain.getCommandStack();
		
		// Force the operation history to clear itself of our operations.
		OperationHistoryFactory.getOperationHistory().setLimit(
			((WorkspaceCommandStackImpl)stack).getDefaultUndoContext(), 0);
		OperationHistoryFactory.getOperationHistory().setLimit(
			((WorkspaceCommandStackImpl)stack).getDefaultUndoContext(), 20);
		
		stack.saveIsDone();
		
		assertFalse(stack.isSaveNeeded());
		
		stack.execute(op);
		
		assertTrue(stack.isSaveNeeded());
		
		stack.undo();
		
		assertFalse(stack.isSaveNeeded());
		
		stack.redo();
		
		assertTrue(stack.isSaveNeeded());
		
		stack.saveIsDone();
		
		assertFalse(stack.isSaveNeeded());
		
		stack.execute(op);
		
		assertTrue(stack.isSaveNeeded());
	}
	
    /**
     * Test that run-time exceptions in a trigger command cause rollback of
     * the whole transaction.
     */
    public void test_triggerRollback_146853() {
        final RuntimeException error = new RuntimeException();
        
        ResourceSetListener testListener = new TriggerListener() {
        	protected Command trigger(TransactionalEditingDomain domain, Notification notification) {
        		return new RecordingCommand(domain, "Error") { //$NON-NLS-1$
        			protected void doExecute() {
        				throw error;
        			}};
        	}};
        
        LogCapture logCapture = new LogCapture(
        		getCommandStack(), EMFWorkspacePlugin.getPlugin().getBundle());
            
        try {
            domain.addResourceSetListener(testListener);
            
            domain.getCommandStack().execute(new RecordingCommand(domain) {
                protected void doExecute() {
                    root.getWriters().clear();
                    root.getStock().clear();
                    root.getBranches().clear();
                }});
            
            // verify that the exception was duly logged
            logCapture.assertLogged(error);
            
            // verify that rollback occurred
            assertFalse(root.getWriters().isEmpty());
            assertFalse(root.getStock().isEmpty());
            assertFalse(root.getBranches().isEmpty());
        } finally {
            logCapture.stop();
            domain.removeResourceSetListener(testListener);
        }
    }
	
    /**
     * Test that OperationCanceledException in a trigger command causes
     * rollback of the whole transaction, without any log message (because it
     * is a normal condition).
     */
    public void test_triggerRollback_cancel_146853() {
        final RuntimeException error = new OperationCanceledException();
        
        ResourceSetListener testListener = new TriggerListener() {
        	protected Command trigger(TransactionalEditingDomain domain, Notification notification) {
        		return new RecordingCommand(domain, "Error") { //$NON-NLS-1$
        			protected void doExecute() {
        				throw error;
        			}};
        	}};
        
        LogCapture logCapture = new LogCapture(
        		getCommandStack(), EMFWorkspacePlugin.getPlugin().getBundle());
            
        try {
            domain.addResourceSetListener(testListener);
            
            domain.getCommandStack().execute(new RecordingCommand(domain) {
                protected void doExecute() {
                    root.getWriters().clear();
                    root.getStock().clear();
                    root.getBranches().clear();
                }});
            
            // verify that the exception was *not* logged
            IStatus log = logCapture.getLastLog();
            assertNull(log);
            
            // verify that rollback occurred
            assertFalse(root.getWriters().isEmpty());
            assertFalse(root.getStock().isEmpty());
            assertFalse(root.getBranches().isEmpty());
        } finally {
            logCapture.stop();
            domain.removeResourceSetListener(testListener);
        }
    }
	
    /**
     * Test that run-time exceptions in a trigger command cause rollback of
     * the whole transaction when executing an AbstractEMFOperation.
     */
    public void test_triggerRollback_operation_146853() {
        final RuntimeException error = new RuntimeException();
        
        ResourceSetListener testListener = new TriggerListener() {
        	protected Command trigger(TransactionalEditingDomain domain, Notification notification) {
        		return new RecordingCommand(domain, "Error") { //$NON-NLS-1$
        			protected void doExecute() {
        				throw error;
        			}};
        	}};
        
        try {
            domain.addResourceSetListener(testListener);
            
            try {
	            IStatus status = new AbstractEMFOperation(domain, "test") { //$NON-NLS-1$
	                protected IStatus doExecute(IProgressMonitor monitor, IAdaptable info) {
	                    root.getWriters().clear();
	                    root.getStock().clear();
	                    root.getBranches().clear();
	                    
	                    return Status.OK_STATUS;
	                }}.execute(null, null);
	            
	            assertEquals(IStatus.ERROR, status.getSeverity());
            } catch (ExecutionException e) {
            	fail("Execution failed: " + e.getLocalizedMessage()); //$NON-NLS-1$
            }
            
            // verify that rollback occurred
            assertFalse(root.getWriters().isEmpty());
            assertFalse(root.getStock().isEmpty());
            assertFalse(root.getBranches().isEmpty());
        } finally {
            domain.removeResourceSetListener(testListener);
        }
    }
	
    /**
     * Test that OperationCanceledException in a trigger command causes
     * rollback of the whole transaction, without any log message (because it
     * is a normal condition) when executing an AbstractEMFOperation.
     */
    public void test_triggerRollback_operation_cancel_146853() {
        final RuntimeException error = new OperationCanceledException();
        
        ResourceSetListener testListener = new TriggerListener() {
        	protected Command trigger(TransactionalEditingDomain domain, Notification notification) {
        		return new RecordingCommand(domain, "Error") { //$NON-NLS-1$
        			protected void doExecute() {
        				throw error;
        			}};
        	}};
        
        try {
            domain.addResourceSetListener(testListener);
            
            try {
	            IStatus status = new AbstractEMFOperation(domain, "test") { //$NON-NLS-1$
	                protected IStatus doExecute(IProgressMonitor monitor, IAdaptable info) {
	                    root.getWriters().clear();
	                    root.getStock().clear();
	                    root.getBranches().clear();
	                    
	                    return Status.OK_STATUS;
	                }}.execute(null, null);
	            
	            assertEquals(IStatus.CANCEL, status.getSeverity());
            } catch (ExecutionException e) {
            	fail("Execution failed: " + e.getLocalizedMessage()); //$NON-NLS-1$
            }
            
            // verify that rollback occurred
            assertFalse(root.getWriters().isEmpty());
            assertFalse(root.getStock().isEmpty());
            assertFalse(root.getBranches().isEmpty());
        } finally {
            domain.removeResourceSetListener(testListener);
        }
    }
	
	//
	// Fixture methods
	//
	
	protected void doSetUp() throws Exception {
		super.doSetUp();
		
		defaultContext = ((IWorkspaceCommandStack) getCommandStack()).getDefaultUndoContext();
	}
	
	protected void doTearDown() throws Exception {
		defaultContext = null;
		
		super.doTearDown();
	}
	
	ResourceUndoContext getResourceUndoContext(IUndoableOperation operation) {
		ResourceUndoContext result = null;
		IUndoContext[] contexts = operation.getContexts();
		for (int i = 0; (result == null) && (i < contexts.length); i++) {
			if (contexts[i] instanceof ResourceUndoContext) {
				result = (ResourceUndoContext) contexts[i];
			}
		}
		
		return result;
	}
}
