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
 * $Id: AllTests.java,v 1.1 2006/01/03 20:51:13 cdamus Exp $
 */
package org.eclipse.emf.transaction.tests;

import java.util.Arrays;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

import org.eclipse.core.runtime.IPlatformRunnable;
import org.eclipse.emf.transaction.multithread.tests.AbstractMultithreadTest;
import org.eclipse.emf.transaction.util.tests.InternalUtilTests;

/**
 * Master JUnit test suite for the <em>EMF-TX API</em>.
 * 
 * @author Christian W. Damus (cdamus)
 */
public class AllTests
	extends TestCase
	implements IPlatformRunnable {

	public AllTests() {
		super(""); //$NON-NLS-1$
	}

	public static Test suite() {
		TestSuite suite = new TestSuite("EMF-TX JUnit Tests"); //$NON-NLS-1$

		suite.addTest(InternalUtilTests.suite());
		suite.addTest(BasicTransactionTest.suite());
		suite.addTest(ValidationRollbackTest.suite());
		suite.addTest(ResourceSetListenersTest.suite());
		suite.addTest(NotificationFilterTest.suite());
		suite.addTest(TransactionOptionsText.suite());
		suite.addTest(UndoRedoTest.suite());
		suite.addTest(EditingDomainRegistryTest.suite());
		suite.addTest(AbstractMultithreadTest.suite());

		return suite;
	}

	public Object run(Object args)
		throws Exception {

		TestRunner.run(suite());
		return Arrays
			.asList(new String[] {"Please see raw test suite output for details."}); //$NON-NLS-1$
	}
}