/**
 * <copyright>
 *
 * Copyright (c) 2005 IBM Corporation and others.
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
 * $Id: OperationChangeDescriptionTest.java,v 1.1 2006/01/30 16:26:01 cdamus Exp $
 */
package org.eclipse.emf.workspace.util.tests;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.emf.workspace.tests.fixtures.ExternalDataOperation;
import org.eclipse.emf.workspace.util.OperationChangeDescription;

/**
 * Tests the {@link OperationChangeDescription} class.
 *
 * @author Christian W. Damus (cdamus)
 */
public class OperationChangeDescriptionTest extends TestCase {
	
	private String externalData[];
	private String initialValue;
	private String newValue;
	private IUndoableOperation operation;
	private OperationChangeDescription change;
	
	public OperationChangeDescriptionTest(String name) {
		super(name);
	}

	public static Test suite() {
		return new TestSuite(OperationChangeDescriptionTest.class, "Operation Change Tests"); //$NON-NLS-1$
	}
	
	/**
	 * Tests that no EMF changes are provided, though the API contract of the
	 * change description is satisfied.
	 */
	public void test_emfChanges() {
		assertTrue(change.getObjectChanges().isEmpty());
		assertTrue(change.getResourceChanges().isEmpty());
		assertTrue(change.getObjectsToAttach().isEmpty());
		assertTrue(change.getObjectsToDetach().isEmpty());
	}
	
	/**
	 * Tests the canApply() method.
	 */
	public void test_canApply() {
		assertTrue(change.canApply());
	}
	
	/**
	 * Tests the apply() method.
	 */
	public void test_apply() {
		change.apply();
		
		assertEquals(initialValue, externalData[0]);
		
		// can no longer apply because we forgot our operation
		assertFalse(change.canApply());
	}
	
	/**
	 * Tests the applyAndReverse() method.
	 */
	public void test_applyAndReverse() {
		change.applyAndReverse();
		
		assertEquals(initialValue, externalData[0]);

		assertTrue(change.canApply());
		
		change.applyAndReverse();
		
		assertEquals(newValue, externalData[0]);

		assertTrue(change.canApply());
		
		change.applyAndReverse();
		
		assertEquals(initialValue, externalData[0]);

		assertTrue(change.canApply());
	}
	
	//
	// Fixture methods
	//
	
	protected void setUp()
		throws Exception {
		
		initialValue = "Initial value"; //$NON-NLS-1$
		newValue = "New value"; //$NON-NLS-1$
		externalData = new String[] {initialValue};
		operation = new ExternalDataOperation(externalData, newValue);
		operation.execute(new NullProgressMonitor(), null);
		change = new OperationChangeDescription(operation, null);
		
		assertEquals(newValue, externalData[0]);
	}
	
	protected void tearDown()
		throws Exception {
		
		externalData = null;
		operation = null;
		change = null;
		initialValue = null;
		newValue = null;
	}
	
	/**
	 * Records a failure due to an exception that should not have been thrown.
	 * 
	 * @param e the exception
	 */
	protected void fail(Exception e) {
		e.printStackTrace();
		fail("Should not have thrown: " + e.getLocalizedMessage()); //$NON-NLS-1$
	}
}
