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
package org.eclipse.emf.transaction.util.tests;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Suite of tests for the utility classes in the EMF-TX API implementation.
 * 
 * @author Christian W. Damus (cdamus)
 */
public class InternalUtilTests
	extends TestCase {

	public InternalUtilTests() {
		super(""); //$NON-NLS-1$
	}

	public static Test suite() {
		TestSuite suite = new TestSuite("Internal Utility Tests"); //$NON-NLS-1$

		suite.addTest(LockTest.suite());
		suite.addTest(CompositeChangeDescriptionTest.suite());
		suite.addTest(TransactionUtilTests.suite());

		return suite;
	}
}