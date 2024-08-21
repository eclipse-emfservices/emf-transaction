/**
 * Copyright (c) 2009, 2024 Obeo
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Mariot Chauvin <mariot.chauvin@obeo.fr> - initial API and implementation
 */
package org.eclipse.emf.transaction.multithread.tests;

import java.io.File;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.commands.operations.DefaultOperationHistory;
import org.eclipse.core.commands.operations.IOperationHistory;
import org.eclipse.core.runtime.ILogListener;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.impl.ENotificationImpl;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.internal.EMFTransactionPlugin;
import org.eclipse.emf.workspace.WorkspaceEditingDomainFactory;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * This class contains a test case for EMF transaction.
 * 
 * @author mchauvin
 */
public class EMFTransansactionTest extends TestCase {

	private TransactionalEditingDomain editingDomain;

	private UncaughtExceptionHandler exceptionHandler;

	private ILogListener listener;

	private AtomicBoolean errorDetected = new AtomicBoolean();

	public static Test suite() {
		return new TestSuite(EMFTransansactionTest.class, "Concurrent Transaction Tests"); //$NON-NLS-1$
	}

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		exceptionHandler = new UncaughtExceptionHandler() {
			public void uncaughtException(Thread t, Throwable e) {
				errorDetected.set(true);
			}
		};
	}

	/**
	 * Due to threads synchronization complexity, this test may succeed. It should
	 * be run several times (at least 4) to be sure than the bug is not present.
	 * 
	 * @throws Exception
	 */
	public void testSynchronizationBug() throws Exception {
		/* create a resource set */
		final ResourceSet rset = new ResourceSetImpl();
		/* create an editing domain */
		editingDomain = createEditingDomain(rset);

		/* initialize the first model */
		final EPackage ePackage = EcoreFactory.eINSTANCE.createEPackage();
		final EClass eClass = EcoreFactory.eINSTANCE.createEClass();
		ePackage.getEClassifiers().add(eClass);
		final EReference eReference = EcoreFactory.eINSTANCE.createEReference();
		eClass.getEReferences().add(eReference);

		/* create resource and and add it initialized model */
		final URI fileUri = URI.createFileURI(new File("test.ecore").getAbsolutePath());
		final Resource rs = rset.createResource(fileUri);
		editingDomain.getCommandStack().execute(new RecordingCommand(editingDomain) {
			@Override
			protected void doExecute() {
				rs.getContents().add(ePackage);
			}
		});

		/* initialize the second model */
		final EPackage ePackage2 = EcoreFactory.eINSTANCE.createEPackage();
		final EClass eClass2 = EcoreFactory.eINSTANCE.createEClass();
		ePackage2.getEClassifiers().add(eClass2);
		final EReference eReference2 = EcoreFactory.eINSTANCE.createEReference();
		eClass2.getEReferences().add(eReference2);

		/* create resource and and add it initialized model */
		final URI file2Uri = URI.createFileURI(new File("test2.ecore").getAbsolutePath());
		final Resource rs2 = rset.createResource(file2Uri);
		editingDomain.getCommandStack().execute(new RecordingCommand(editingDomain) {
			@Override
			protected void doExecute() {
				rs2.getContents().add(ePackage2);
			}
		});

		/* set a link between first and second class */
		editingDomain.getCommandStack().execute(new RecordingCommand(editingDomain) {
			@Override
			protected void doExecute() {
				eReference.setEOpposite(eReference2);
			}
		});

		Thread.setDefaultUncaughtExceptionHandler(exceptionHandler);

		listener = (IStatus status, String plugin) -> {
			if (status.getSeverity() == IStatus.ERROR) {
				errorDetected.set(true);
			}
		};

		EMFTransactionPlugin.getPlugin().getLog().addLogListener(listener);

		final Collection<Thread> threads = new ArrayList<Thread>();
		/* Launch a unload and a resolution */
		for (int i = 0; i < 100; i++) {
			threads.add(launchNotificationInANewThread(eReference, eReference2));
		}

		for (final Thread thread : threads) {
			thread.join();
		}

		EMFTransactionPlugin.getPlugin().getLog().removeLogListener(listener);

		/* an exception occurs in another thread */
		if (errorDetected.get()) {
			fail();
		}
	}

	private TransactionalEditingDomain createEditingDomain(ResourceSet rset) {
		IOperationHistory history = new DefaultOperationHistory();
		return WorkspaceEditingDomainFactory.INSTANCE.createEditingDomain(rset, history);
	}

	private Thread launchNotificationInANewThread(final EReference ref1, final EReference ref2) throws Exception {
		final Thread t = new Thread(() -> {
			for (int i = 0; i < 200; i++) {
				ref1.eNotify(new ENotificationImpl((InternalEObject) ref1, Notification.RESOLVE,
						EcorePackage.EREFERENCE__EOPPOSITE, null, ref2));
			}
		});
		t.start();
		return t;
	}

}