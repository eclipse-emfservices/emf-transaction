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
 * $Id: WorkbenchCommandStackTest.java,v 1.5 2006/05/17 21:18:28 cmcgee Exp $
 */
package org.eclipse.emf.workspace.tests;

import java.util.Collection;

import javax.swing.undo.AbstractUndoableEdit;

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
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.command.BasicCommandStack;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.edit.command.SetCommand;
import org.eclipse.emf.examples.extlibrary.EXTLibraryFactory;
import org.eclipse.emf.examples.extlibrary.EXTLibraryPackage;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.ResourceSetChangeEvent;
import org.eclipse.emf.transaction.ResourceSetListenerImpl;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.workspace.AbstractEMFOperation;
import org.eclipse.emf.workspace.EMFCommandOperation;
import org.eclipse.emf.workspace.EMFOperationCommand;
import org.eclipse.emf.workspace.IWorkspaceCommandStack;
import org.eclipse.emf.workspace.ResourceUndoContext;
import org.eclipse.emf.workspace.WorkspaceEditingDomainFactory;
import org.eclipse.emf.workspace.impl.WorkspaceCommandStackImpl;
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
