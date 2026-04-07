/**
 * Copyright (c) 2008 Zeligsoft Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Zeligsoft - Initial API and implementation
 */
package org.eclipse.emf.transaction.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.eclipse.emf.examples.extlibrary.Book;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.TransactionalEditingDomainEvent;
import org.eclipse.emf.transaction.TransactionalEditingDomainListener;
import org.eclipse.emf.transaction.util.TransactionUtil;
import org.junit.jupiter.api.Test;

/**
 * Tests the dispatching of life-cycle events to listeners.
 *
 * @author Christian W. Damus (cdamus)
 */
public class LifecycleListenersTest extends AbstractTest {

	private LifecycleListener listener;

	/**
	 * Tests that we get events in the right sequence in a normal commit scenario.
	 */
	@Test
	public void test_eventSequence_commit() {
		final Book book = (Book) find("root/Root Book");
		assertNotNull(book);

		startWriting();
		book.setTitle("New Title");
		startWriting();
		book.setTitle("Another Title");
		commit();
		commit();

		List<Integer> expected = Arrays.asList(TransactionalEditingDomainEvent.TRANSACTION_STARTING,
				TransactionalEditingDomainEvent.TRANSACTION_STARTED,
				TransactionalEditingDomainEvent.TRANSACTION_CLOSING,
				// the transaction for post-commit notifications is a root
				TransactionalEditingDomainEvent.TRANSACTION_STARTING,
				TransactionalEditingDomainEvent.TRANSACTION_STARTED,
				TransactionalEditingDomainEvent.TRANSACTION_CLOSING, TransactionalEditingDomainEvent.TRANSACTION_CLOSED,
				// the original root transaction is closed after post-commit
				TransactionalEditingDomainEvent.TRANSACTION_CLOSED);

		assertEquals(expected, listener.eventTypesReceived, "Wrong event sequence");
	}

	/**
	 * Tests that we get events in the right sequence in a normal commit scenario.
	 */
	@Test
	public void test_eventSequence_rollback() {
		final Book book = (Book) find("root/Root Book");
		assertNotNull(book);

		startWriting();

		book.setTitle("New Title");

		startWriting();

		book.setTitle("Another Title");

		commit();

		rollback();

		List<Integer> expected = Arrays.asList(TransactionalEditingDomainEvent.TRANSACTION_STARTING,
				TransactionalEditingDomainEvent.TRANSACTION_STARTED,
				TransactionalEditingDomainEvent.TRANSACTION_CLOSING,
				// without any notifications to send, there is no additional
				// transaction for post-commit
				TransactionalEditingDomainEvent.TRANSACTION_CLOSED);

		assertEquals(expected, listener.eventTypesReceived, "Wrong event sequence");
	}

	//
	// Test framework
	//

	@Override
	protected void doSetUp() throws Exception {
		super.doSetUp();
		listener = new LifecycleListener();
		TransactionUtil.getAdapter(domain, TransactionalEditingDomain.Lifecycle.class)
				.addTransactionalEditingDomainListener(listener);
	}

	@Override
	protected void doTearDown() throws Exception {
		TransactionUtil.getAdapter(domain, TransactionalEditingDomain.Lifecycle.class)
				.removeTransactionalEditingDomainListener(listener);
		listener = null;
		super.doTearDown();
	}

	private class LifecycleListener implements TransactionalEditingDomainListener {

		List<Integer> eventTypesReceived = new java.util.ArrayList<>();

		@Override
		public void editingDomainDisposing(TransactionalEditingDomainEvent event) {
			assertNull(event.getTransaction());
			eventTypesReceived.add(event.getEventType());
		}

		@Override
		public void transactionClosed(TransactionalEditingDomainEvent event) {
			assertNotNull(event.getTransaction());
			assertFalse(event.getTransaction().isActive());
			eventTypesReceived.add(event.getEventType());
		}

		@Override
		public void transactionClosing(TransactionalEditingDomainEvent event) {
			assertNotNull(event.getTransaction());
			assertTrue(event.getTransaction().isActive());
			eventTypesReceived.add(event.getEventType());
		}

		@Override
		public void transactionInterrupted(TransactionalEditingDomainEvent event) {
			assertNotNull(event.getTransaction());
			assertFalse(event.getTransaction().isActive());
			eventTypesReceived.add(event.getEventType());
		}

		@Override
		public void transactionStarted(TransactionalEditingDomainEvent event) {
			assertNotNull(event.getTransaction());
			assertTrue(event.getTransaction().isActive());
			eventTypesReceived.add(event.getEventType());
		}

		@Override
		public void transactionStarting(TransactionalEditingDomainEvent event) {
			assertNotNull(event.getTransaction());
			assertFalse(event.getTransaction().isActive());
			eventTypesReceived.add(event.getEventType());
		}
	}
}
