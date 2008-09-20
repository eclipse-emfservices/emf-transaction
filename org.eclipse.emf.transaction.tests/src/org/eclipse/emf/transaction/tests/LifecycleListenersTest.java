/**
 * <copyright>
 * 
 * Copyright (c) 2008 Zeligsoft Inc. and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *   Zeligsoft - Initial API and implementation
 * 
 * </copyright>
 *
 * $Id: LifecycleListenersTest.java,v 1.1 2008/09/20 21:22:43 cdamus Exp $
 */

package org.eclipse.emf.transaction.tests;

import java.util.Arrays;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.emf.examples.extlibrary.Book;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.TransactionalEditingDomainEvent;
import org.eclipse.emf.transaction.TransactionalEditingDomainListener;
import org.eclipse.emf.transaction.util.TransactionUtil;

/**
 * Tests the dispatching of life-cycle events to listeners.
 * 
 * @author Christian W. Damus (cdamus)
 */
@SuppressWarnings("nls")
public class LifecycleListenersTest
		extends AbstractTest {

	private LifecycleListener listener;

	public LifecycleListenersTest(String name) {
		super(name);
	}

	public static Test suite() {
		return new TestSuite(LifecycleListenersTest.class,
			"Editing Domain Life-cycle Event Tests");
	}

	/**
	 * Tests that we get events in the right sequence in a normal commit
	 * scenario.
	 */
	public void test_eventSequence_commit() {
		final Book book = (Book) find("root/Root Book");
		assertNotNull(book);

		startWriting();

		book.setTitle("New Title");
		
		startWriting();
		
		book.setTitle("Another Title");
		
		commit();
		
		commit();

		List<Integer> expected = Arrays.asList(
			TransactionalEditingDomainEvent.TRANSACTION_STARTING,
			TransactionalEditingDomainEvent.TRANSACTION_STARTED,
			TransactionalEditingDomainEvent.TRANSACTION_CLOSING,
			// the transaction for post-commit notifications is a root
			TransactionalEditingDomainEvent.TRANSACTION_STARTING,
			TransactionalEditingDomainEvent.TRANSACTION_STARTED,
			TransactionalEditingDomainEvent.TRANSACTION_CLOSING,
			TransactionalEditingDomainEvent.TRANSACTION_CLOSED,
			// the original root transaction is closed after post-commit
			TransactionalEditingDomainEvent.TRANSACTION_CLOSED);

		assertEquals("Wrong event sequence", expected,
			listener.eventTypesReceived);
	}

	/**
	 * Tests that we get events in the right sequence in a normal commit
	 * scenario.
	 */
	public void test_eventSequence_rollback() {
		final Book book = (Book) find("root/Root Book");
		assertNotNull(book);

		startWriting();

		book.setTitle("New Title");
		
		startWriting();
		
		book.setTitle("Another Title");
		
		commit();
		
		rollback();

		List<Integer> expected = Arrays.asList(
			TransactionalEditingDomainEvent.TRANSACTION_STARTING,
			TransactionalEditingDomainEvent.TRANSACTION_STARTED,
			TransactionalEditingDomainEvent.TRANSACTION_CLOSING,
			// without any notifications to send, there is no additional
			// transaction for post-commit
			TransactionalEditingDomainEvent.TRANSACTION_CLOSED);

		assertEquals("Wrong event sequence", expected,
			listener.eventTypesReceived);
	}

	//
	// Test framework
	//

	@Override
	protected void doSetUp()
			throws Exception {

		super.doSetUp();

		listener = new LifecycleListener();
		TransactionUtil.getAdapter(domain,
			TransactionalEditingDomain.Lifecycle.class)
			.addTransactionalEditingDomainListener(listener);
	}

	@Override
	protected void doTearDown()
			throws Exception {

		TransactionUtil.getAdapter(domain,
			TransactionalEditingDomain.Lifecycle.class)
			.removeTransactionalEditingDomainListener(listener);
		listener = null;

		super.doTearDown();
	}

	private class LifecycleListener
			implements TransactionalEditingDomainListener {

		List<Integer> eventTypesReceived = new java.util.ArrayList<Integer>();

		public void editingDomainDisposing(TransactionalEditingDomainEvent event) {
			assertNull(event.getTransaction());
			eventTypesReceived.add(event.getEventType());
		}

		public void transactionClosed(TransactionalEditingDomainEvent event) {
			assertNotNull(event.getTransaction());
			assertFalse(event.getTransaction().isActive());
			eventTypesReceived.add(event.getEventType());
		}

		public void transactionClosing(TransactionalEditingDomainEvent event) {
			assertNotNull(event.getTransaction());
			assertTrue(event.getTransaction().isActive());
			eventTypesReceived.add(event.getEventType());
		}

		public void transactionInterrupted(TransactionalEditingDomainEvent event) {
			assertNotNull(event.getTransaction());
			assertFalse(event.getTransaction().isActive());
			eventTypesReceived.add(event.getEventType());
		}

		public void transactionStarted(TransactionalEditingDomainEvent event) {
			assertNotNull(event.getTransaction());
			assertTrue(event.getTransaction().isActive());
			eventTypesReceived.add(event.getEventType());
		}

		public void transactionStarting(TransactionalEditingDomainEvent event) {
			assertNotNull(event.getTransaction());
			assertFalse(event.getTransaction().isActive());
			eventTypesReceived.add(event.getEventType());
		}
	}
}
