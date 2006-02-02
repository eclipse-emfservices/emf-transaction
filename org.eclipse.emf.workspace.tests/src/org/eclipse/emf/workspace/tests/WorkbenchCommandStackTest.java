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
 * $Id: WorkbenchCommandStackTest.java,v 1.3 2006/02/02 16:24:19 cdamus Exp $
 */
package org.eclipse.emf.workspace.tests;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.edit.command.SetCommand;
import org.eclipse.emf.examples.extlibrary.EXTLibraryPackage;
import org.eclipse.emf.workspace.EMFCommandOperation;
import org.eclipse.emf.workspace.IWorkspaceCommandStack;
import org.eclipse.emf.workspace.ResourceUndoContext;
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
