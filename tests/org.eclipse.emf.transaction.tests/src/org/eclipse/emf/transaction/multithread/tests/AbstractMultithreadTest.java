/**
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   IBM - Initial API and implementation
 */
package org.eclipse.emf.transaction.multithread.tests;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.tests.AbstractTest;

/**
 * Abstract JUnit test suite for the <em>EMF-TX API</em> multi-threading tests.
 * 
 * @author Christian W. Damus (cdamus)
 */
public class AbstractMultithreadTest
	extends TestCase {

	private TransactionalEditingDomain domain = null;
	
	public AbstractMultithreadTest() {
		super(""); //$NON-NLS-1$
	}

	public static Test suite() {
		TestSuite suite = new TestSuite("Multi-threading Tests"); //$NON-NLS-1$

		suite.addTest(ReadOperationTest.suite());
		suite.addTest(WriteOperationTest.suite());
		suite.addTest(ReadWriteOperationTest.suite());
		suite.addTest(EMFTransansactionTest.suite());

		return suite;
	}
	
	//
	// Fixture methods
	//
	
	protected TransactionalEditingDomain getDomain() {
		return domain;
	}
	
	@Override
	protected void setUp()
		throws Exception {
		
		AbstractTest.trace("===> Begin : " + getName()); //$NON-NLS-1$
		
		super.setUp();
		
		domain = TransactionalEditingDomain.Factory.INSTANCE.createEditingDomain();
	}
	
	@Override
	protected void tearDown()
		throws Exception {
		
		domain = null;
		
		AbstractTest.trace("===> End   : " + getName()); //$NON-NLS-1$
		
		super.tearDown();
	}
}
