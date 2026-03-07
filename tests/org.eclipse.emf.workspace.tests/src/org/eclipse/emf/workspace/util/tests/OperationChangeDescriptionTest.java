/**
 * Copyright (c) 2005, 2026 IBM Corporation and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   IBM - Initial API and implementation
 */
package org.eclipse.emf.workspace.util.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.emf.workspace.tests.fixtures.ExternalDataOperation;
import org.eclipse.emf.workspace.util.OperationChangeDescription;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the {@link OperationChangeDescription} class.
 *
 * @author Christian W. Damus (cdamus)
 */
public class OperationChangeDescriptionTest {
	
	private String[] externalData;
	private String initialValue;
	private String newValue;
	private IUndoableOperation operation;
	private OperationChangeDescription change;

	
	/**
	 * Tests that no EMF changes are provided, though the API contract of the
	 * change description is satisfied.
	 */
	@Test
	public void test_emfChanges() {
		assertTrue(change.getObjectChanges().isEmpty());
		assertTrue(change.getResourceChanges().isEmpty());
		assertTrue(change.getObjectsToAttach().isEmpty());
		assertTrue(change.getObjectsToDetach().isEmpty());
	}
	
	/**
	 * Tests the canApply() method.
	 */
	@Test
	public void test_canApply() {
		assertTrue(change.canApply());
	}
	
	/**
	 * Tests the apply() method.
	 */
	@Test
	public void test_apply() {
		change.apply();
		
		assertEquals(initialValue, externalData[0]);
		
		// can no longer apply because we forgot our operation
		assertFalse(change.canApply());
	}
	
	/**
	 * Tests the applyAndReverse() method.
	 */
	@Test
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
	
	@Before
	public void setUp()
		throws Exception {
		
		initialValue = "Initial value"; 
		newValue = "New value"; 
		externalData = new String[] {initialValue};
		operation = new ExternalDataOperation(externalData, newValue);
		operation.execute(new NullProgressMonitor(), null);
		change = new OperationChangeDescription(operation, null);
		
		assertEquals(newValue, externalData[0]);
	}
	
	@After
	public void tearDown()
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
		Assert.fail("Should not have thrown: " + e.getLocalizedMessage()); 
	}
}
