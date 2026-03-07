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

import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.tests.AbstractTest;
import org.junit.After;
import org.junit.Before;

/**
 * Abstract JUnit test suite for the <em>EMF-TX API</em> multi-threading tests.
 * 
 * @author Christian W. Damus (cdamus)
 */
public class AbstractMultithreadTest {

	private TransactionalEditingDomain domain = null;

	//
	// Fixture methods
	//

	protected TransactionalEditingDomain getDomain() {
		return domain;
	}

	@Before
	public void setUp() throws Exception {
		AbstractTest.trace("===> Begin : " + this.getClass().getName());
		domain = TransactionalEditingDomain.Factory.INSTANCE.createEditingDomain();
	}

	@After
	public void tearDown() throws Exception {
		domain = null;
		AbstractTest.trace("===> End   : " + this.getClass().getName());
	}
}
