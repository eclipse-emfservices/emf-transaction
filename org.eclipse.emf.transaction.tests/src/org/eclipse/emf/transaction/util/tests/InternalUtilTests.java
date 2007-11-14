/**
 * <copyright>
 *
 * Copyright (c) 2005, 2007 IBM Corporation and others.
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
 * $Id: InternalUtilTests.java,v 1.3 2007/11/14 18:14:13 cdamus Exp $
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