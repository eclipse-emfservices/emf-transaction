/**
 * <copyright>
 *
 * Copyright (c) 2005, 2015 IBM Corporation, Zeligsoft Inc., and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   IBM - Initial API and implementation
 *   Zeligsoft - Bugs 145877, 248717
 *   Christian W. Damus - Bug 460206
 *
 * </copyright>
 */
package org.eclipse.emf.transaction.tests;

import java.util.Arrays;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

import org.eclipse.emf.transaction.multithread.tests.AbstractMultithreadTest;
import org.eclipse.emf.transaction.util.tests.InternalUtilTests;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

/**
 * Master JUnit test suite for the <em>EMF-TX API</em>.
 * 
 * @author Christian W. Damus (cdamus)
 */
public class AllTests
	extends TestCase
	implements IApplication {

	public AllTests() {
		super(""); //$NON-NLS-1$
	}

	public static Test suite() {
		TestSuite suite = new TestSuite("EMF-TX JUnit Tests"); //$NON-NLS-1$

		suite.addTest(InternalUtilTests.suite());
		suite.addTest(BasicTransactionTest.suite());
		suite.addTest(EditingDomainTest.suite());
		suite.addTest(ValidationRollbackTest.suite());
		suite.addTest(ResourceSetListenersTest.suite());
		suite.addTest(NotificationFilterTest.suite());
		suite.addTest(LifecycleListenersTest.suite());
		suite.addTest(TransactionOptionsTest.suite());
		suite.addTest(TransactionChangeRecorderTest.suite());
		suite.addTest(UndoRedoTest.suite());
		suite.addTest(EditingDomainRegistryTest.suite());
		suite.addTest(AbstractMultithreadTest.suite());
		suite.addTest(PrivilegedRunnableTest.suite());
		suite.addTest(EditingDomainValidatorTest.suite());
		suite.addTest(ValidateEditTest.suite());
		suite.addTest(JobManagerSuspensionDeadlockTest.suite());
		suite.addTest(RecordingCommandTest.suite());
		suite.addTest(ChangeDescriptionTest.suite());
		suite.addTest(PerformanceTest.suite());
		suite.addTest(MemoryLeakTest.suite());
		return suite;
	}

	public Object start(IApplicationContext context)
		throws Exception {
		
		TestRunner.run(suite());
		
		return Arrays.asList(new String[] {
			"Please see raw test suite output for details."}); //$NON-NLS-1$
	}

	public void stop() {
		// nothing to do
	}
}
