/**
 * <copyright>
 *
 * Copyright (c) 2005, 2008 IBM Corporation, Zeligsoft Inc., and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   IBM - Initial API and implementation
 *   IBM - Bug 244654
 *   Zeligsoft - Bug 244654 (touch ups)
 *
 * </copyright>
 *
 * $Id: WorkbenchCommandStackTest.java,v 1.12.2.1 2008/10/04 17:49:18 cdamus Exp $
 */
package org.eclipse.emf.workspace.tests;

import java.util.Arrays;
import java.util.EventObject;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.AbstractOperation;
import org.eclipse.core.commands.operations.IOperationHistory;
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
import org.eclipse.emf.common.command.CommandStack;
import org.eclipse.emf.common.command.CommandStackListener;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.edit.command.SetCommand;
import org.eclipse.emf.examples.extlibrary.Book;
import org.eclipse.emf.examples.extlibrary.EXTLibraryFactory;
import org.eclipse.emf.examples.extlibrary.EXTLibraryPackage;
import org.eclipse.emf.examples.extlibrary.Library;
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
import org.eclipse.emf.workspace.tests.fixtures.LibraryDefaultNameTrigger;
import org.eclipse.emf.workspace.tests.fixtures.LogCapture;
import org.eclipse.emf.workspace.tests.fixtures.NullCommand;
import org.eclipse.emf.workspace.tests.fixtures.SelfOpeningEMFCompositeOperation;


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
			@Override
			public boolean isPrecommitOnly() {
				return true;
			}
			
			@Override
			public Command transactionAboutToCommit(ResourceSetChangeEvent event)
				throws RollbackException {
				
				IUndoableOperation op = new AbstractOperation("") { //$NON-NLS-1$
					@Override
					public IStatus execute(IProgressMonitor monitor, IAdaptable info)
						throws ExecutionException {
						return Status.OK_STATUS;
					}
	
					@Override
					public IStatus redo(IProgressMonitor monitor, IAdaptable info)
						throws ExecutionException {
						return Status.OK_STATUS;
					}
	
					@Override
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
		IUndoContext resCtx = new ResourceUndoContext(domain, r);
        
		AbstractEMFOperation op = new AbstractEMFOperation(domain, "") { //$NON-NLS-1$
			@Override
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
        List<IUndoContext> opContexts = Arrays.asList(op.getContexts());
		assertTrue(opContexts.contains(resCtx));
        assertTrue(opContexts.contains(undoContext));
		
		op.removeContext(undoContext);
        op.removeContext(resCtx);
		
		try {
			OperationHistoryFactory.getOperationHistory().execute(op, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			e.printStackTrace();
			fail();
		}
		
        assertNotNull(op.getContexts());
        opContexts = Arrays.asList(op.getContexts());
        assertTrue(opContexts.contains(resCtx));
        assertTrue(opContexts.contains(undoContext));
        
        op.removeContext(undoContext);
        op.removeContext(resCtx);
	}
	
	public void testSaveIsDoneAPIs() {
		TransactionalEditingDomain domain = WorkspaceEditingDomainFactory.INSTANCE.createEditingDomain();
		final Resource r = domain.getResourceSet().createResource(URI.createURI("file://foo.xml")); //$NON-NLS-1$
		
		Command op = new RecordingCommand(domain) {

			@Override
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
    
    public void test_isSaveNeeded_214325() {
        TransactionalEditingDomain domain = WorkspaceEditingDomainFactory.INSTANCE.createEditingDomain();
        final Resource r = domain.getResourceSet().createResource(URI.createURI("file://foo.xml")); //$NON-NLS-1$
        
        Command op = new RecordingCommand(domain) {

            @Override
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
        
        assertFalse(stack.isSaveNeeded());
        
        stack.execute(op);
        
        assertTrue(stack.isSaveNeeded());
        
        stack.saveIsDone();
        
        assertFalse(stack.isSaveNeeded());
        
        stack.undo();
        
        assertTrue(stack.isSaveNeeded());
    }
	
    /**
     * Test that run-time exceptions in a trigger command cause rollback of
     * the whole transaction.
     */
    public void test_triggerRollback_146853() {
        final RuntimeException error = new RuntimeException();
        
        ResourceSetListener testListener = new TriggerListener() {
        	@Override
			protected Command trigger(TransactionalEditingDomain domain, Notification notification) {
        		return new RecordingCommand(domain, "Error") { //$NON-NLS-1$
        			@Override
					protected void doExecute() {
        				throw error;
        			}};
        	}};
        
        LogCapture logCapture = new LogCapture(
        		getCommandStack(), EMFWorkspacePlugin.getPlugin().getBundle());
            
        try {
            domain.addResourceSetListener(testListener);
            
            domain.getCommandStack().execute(new RecordingCommand(domain) {
                @Override
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
        	@Override
			protected Command trigger(TransactionalEditingDomain domain, Notification notification) {
        		return new RecordingCommand(domain, "Error") { //$NON-NLS-1$
        			@Override
					protected void doExecute() {
        				throw error;
        			}};
        	}};
        
        LogCapture logCapture = new LogCapture(
        		getCommandStack(), EMFWorkspacePlugin.getPlugin().getBundle());
            
        try {
            domain.addResourceSetListener(testListener);
            
            domain.getCommandStack().execute(new RecordingCommand(domain) {
                @Override
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
        	@Override
			protected Command trigger(TransactionalEditingDomain domain, Notification notification) {
        		return new RecordingCommand(domain, "Error") { //$NON-NLS-1$
        			@Override
					protected void doExecute() {
        				throw error;
        			}};
        	}};
        
        try {
            domain.addResourceSetListener(testListener);
            
            try {
	            IStatus status = new AbstractEMFOperation(domain, "test") { //$NON-NLS-1$
	                @Override
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
        	@Override
			protected Command trigger(TransactionalEditingDomain domain, Notification notification) {
        		return new RecordingCommand(domain, "Error") { //$NON-NLS-1$
        			@Override
					protected void doExecute() {
        				throw error;
        			}};
        	}};
        
        try {
            domain.addResourceSetListener(testListener);
            
            try {
	            IStatus status = new AbstractEMFOperation(domain, "test") { //$NON-NLS-1$
	                @Override
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
    
    /**
     * Tests that the {@link RecordingCommand} can be used as a trigger command,
     * that in this case it is able correctly to capture its changes for
     * undo/redo.
     */
    public void test_recordingCommandsAsTriggers_bug157103() {
        // one trigger sets default library names
        domain.addResourceSetListener(new LibraryDefaultNameTrigger() {
            @Override
			protected Command trigger(TransactionalEditingDomain domain, Notification notification) {
                Command result = null;
                
                final Library newLibrary = (Library) notification.getNewValue();
                if ((newLibrary.getName() == null) || (newLibrary.getName().length() == 0)) {
                    result = new RecordingCommand(domain) {
                        @Override
						protected void doExecute() {
                            newLibrary.setName("New Library"); //$NON-NLS-1$
                        }};
                }
                
                return result;
            }});
        
        final Library[] newLibrary = new Library[1];
        
        IUndoContext ctx = new UndoContext();
        IUndoableOperation operation = new AbstractEMFOperation(domain, "Test") { //$NON-NLS-1$
            @Override
			protected IStatus doExecute(IProgressMonitor monitor, IAdaptable info) {
                newLibrary[0] = EXTLibraryFactory.eINSTANCE.createLibrary();
                root.getBranches().add(newLibrary[0]);
                
                assertNull(newLibrary[0].getName());
                
                return Status.OK_STATUS;
            }};
        operation.addContext(ctx);
        
        try {
            // add a new library.  Our trigger will set a default name
            history.execute(operation, null, null);
        } catch (ExecutionException e) {
            fail("Failed to execute test operation: " + e.getLocalizedMessage()); //$NON-NLS-1$
        }
        
        startReading();
        
        assertEquals("New Library", newLibrary[0].getName()); //$NON-NLS-1$
        
        commit();
        
        try {
            history.undo(ctx, null, null);
        } catch (ExecutionException e) {
            fail("Failed to undo test operation: " + e.getLocalizedMessage()); //$NON-NLS-1$
        }
        
        assertFalse(root.getBranches().contains(newLibrary[0]));
        assertNull(newLibrary[0].eResource());
        assertNull(newLibrary[0].getName());
        
        try {
            history.redo(ctx, null, null);
        } catch (ExecutionException e) {
            fail("Failed to redo test operation: " + e.getLocalizedMessage()); //$NON-NLS-1$
        }
        
        assertTrue(root.getBranches().contains(newLibrary[0]));
        assertEquals("New Library", newLibrary[0].getName()); //$NON-NLS-1$
    }
    
    /**
     * Tests that, when a command execution is rolled back, the command stack
     * listeners are notified again that the stack is changed, so that they
     * will correctly update themselves if necessary.
     */
    public void test_rollbackNotifiesCommandStackListeners_175725() {
        class TestCSL implements CommandStackListener {
            int invocationCount = 0;
            public void commandStackChanged(EventObject event) {
                invocationCount++;
            }
        }
        
        TestCSL listener = new TestCSL();
        CommandStack stack = domain.getCommandStack();
        stack.addCommandStackListener(listener);
        
        final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
        assertNotNull(book);
        Command command = SetCommand.create(
            domain, book, EXTLibraryPackage.Literals.BOOK__TITLE, null);
        
        try {
            validationEnabled = true;
            stack.execute(command);
        } catch (Exception e) {
            fail(e);
        } finally {
            validationEnabled = false;
            stack.removeCommandStackListener(listener);
        }
        
        assertEquals("Command-stack listener invoked wrong number of times", //$NON-NLS-1$
            1, listener.invocationCount);
        assertFalse("Should not have an undo command", stack.canUndo()); //$NON-NLS-1$
    }
	
    public void test_undoRedoNotifyListeners_173839() {
        class TestCSL implements CommandStackListener {
            int invocationCount = 0;
            public void commandStackChanged(EventObject event) {
                invocationCount++;
            }
        }
        
        TestCSL listener = new TestCSL();
        CommandStack stack = domain.getCommandStack();
        stack.addCommandStackListener(listener);
        
        final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
        assertNotNull(book);
        Command command = SetCommand.create(
            domain, book, EXTLibraryPackage.Literals.BOOK__TITLE, "New Title"); //$NON-NLS-1$
        
        Command undoCmd = null;
        Command redoCmd = null;
        
        try {
            stack.execute(command);
            
            listener.invocationCount = 0;  // clear state
            
            stack.undo();
            redoCmd = stack.getRedoCommand();

            stack.redo();
            undoCmd = stack.getUndoCommand();
        } catch (Exception e) {
            fail(e);
        } finally {
            stack.removeCommandStackListener(listener);
        }
        
        assertEquals("Command-stack listener invoked wrong number of times", //$NON-NLS-1$
            2, listener.invocationCount);
        assertSame(command, undoCmd);
        assertSame(command, redoCmd);
    }
    
    /**
     * Tests that we do not lose track of affected resources when executing
     * operations within open composites (nested operation execution).
     */
    public void test_nestedExecutionInOpenComposite_203352() {
        SelfOpeningEMFCompositeOperation operation = new SelfOpeningEMFCompositeOperation(
            domain) {

            @Override
			protected IStatus doExecute(IOperationHistory history,
                    IProgressMonitor monitor, IAdaptable info)
                throws ExecutionException {

                return history.execute(
                    new AbstractEMFOperation(domain, "Test") { //$NON-NLS-1$

                        @Override
						protected IStatus doExecute(IProgressMonitor monitor,
                                IAdaptable info) {
                            root.getBranches().add(
                                EXTLibraryFactory.eINSTANCE.createLibrary());

                            return Status.OK_STATUS;
                        }
                    }, monitor, info);
            }
        };

        try {
            history.execute(operation, null, null);
        } catch (ExecutionException e) {
            fail("Failed to execute test operation: " + e.getLocalizedMessage()); //$NON-NLS-1$
        }

        IUndoContext expected = new ResourceUndoContext(domain, testResource);
        assertTrue(operation.hasContext(expected));
    }
	
    /**
     * Tests that whatever operation is currently executing while changes occur
     * in some resource, is tagged with an undo context for that resource.
     */
	public void test_nestedExecutionInAbstractOperation_244654() {
		AbstractOperation operation = new AbstractOperation("Test") { //$NON-NLS-1$

			private AbstractEMFOperation delegate = new AbstractEMFOperation(
				domain, "Delegate") { //$NON-NLS-1$

				@Override
				protected IStatus doExecute(IProgressMonitor monitor,
						IAdaptable info)
						throws ExecutionException {

					root.getBranches().add(
						EXTLibraryFactory.eINSTANCE.createLibrary());

					return Status.OK_STATUS;
				}
			};

			@Override
			public IStatus execute(IProgressMonitor monitor, IAdaptable info)
					throws ExecutionException {
				return delegate.execute(monitor, info);
			}

			@Override
			public IStatus redo(IProgressMonitor monitor, IAdaptable info)
					throws ExecutionException {
				return delegate.redo(monitor, info);
			}

			@Override
			public IStatus undo(IProgressMonitor monitor, IAdaptable info)
					throws ExecutionException {
				return delegate.undo(monitor, info);
			}
		};

		assertTrue(operation.getContexts().length == 0);

		try {
			history.execute(operation, null, null);
		} catch (ExecutionException e) {
			fail("Unexpected exception: " + e.getLocalizedMessage()); //$NON-NLS-1$
		}

		IUndoContext expected = new ResourceUndoContext(domain, testResource);
		assertTrue(
			"Operation missing expected context", operation.hasContext(expected)); //$NON-NLS-1$
	}
    
	//
	// Fixture methods
	//
	
	@Override
	protected void doSetUp() throws Exception {
		super.doSetUp();
		
		defaultContext = ((IWorkspaceCommandStack) getCommandStack()).getDefaultUndoContext();
	}
	
	@Override
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
