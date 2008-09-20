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
 * $Id: TransactionalEditingDomainEvent.java,v 1.1 2008/09/20 21:23:08 cdamus Exp $
 */

package org.eclipse.emf.transaction;

import java.util.EventObject;

/**
 * An event object indicating a change in the state of a
 * {@link TransactionalEditingDomain}, usually in some
 * {@linkplain transaction #getTransaction()} life-cycle event.
 * 
 * @author Christian W. Damus (cdamus)
 * 
 * @since 1.3
 */
public class TransactionalEditingDomainEvent
		extends EventObject {

	private static final long serialVersionUID = 7386794800444405389L;

	/**
	 * Event {@linkplain #getEventType() type} indicating the
	 * {@linkplain TransactionalEditingDomainListener#transactionStarting(TransactionalEditingDomainEvent)
	 * transaction starting}.
	 */
	public static final int TRANSACTION_STARTING = 0x01;

	/**
	 * Event {@linkplain #getEventType() type} indicating the
	 * {@linkplain TransactionalEditingDomainListener#transactionInterrupted(TransactionalEditingDomainEvent)
	 * transaction interrupted}.
	 */
	public static final int TRANSACTION_INTERRUPTED = 0x02;

	/**
	 * Event {@linkplain #getEventType() type} indicating the
	 * {@linkplain TransactionalEditingDomainListener#transactionStartied(TransactionalEditingDomainEvent)
	 * transaction started}.
	 */
	public static final int TRANSACTION_STARTED = 0x04;

	/**
	 * Event {@linkplain #getEventType() type} indicating the
	 * {@linkplain TransactionalEditingDomainListener#transactionClosing(TransactionalEditingDomainEvent)
	 * transaction closing}.
	 */
	public static final int TRANSACTION_CLOSING = 0x08;

	/**
	 * Event {@linkplain #getEventType() type} indicating the
	 * {@linkplain TransactionalEditingDomainListener#transactionClosed(TransactionalEditingDomainEvent)
	 * transaction closed}.
	 */
	public static final int TRANSACTION_CLOSED = 0x10;

	/**
	 * Event {@linkplain #getEventType() type} indicating the
	 * {@linkplain TransactionalEditingDomainListener#editingDomainDisposing(TransactionalEditingDomainEvent)
	 * editing domain disposing}.
	 */
	public static final int EDITING_DOMAIN_DISPOSING = 0x20;

	private int type;

	private Transaction transaction;

	/**
	 * Initializes me with my source editing domain and my type.
	 * 
	 * @param source
	 *            my source domain
	 * @param type
	 *            my type
	 */
	public TransactionalEditingDomainEvent(TransactionalEditingDomain source,
			int type) {
		super(source);

		this.type = type;
	}

	/**
	 * Initializes me with my source editing domain and a transaction that
	 * changed.
	 * 
	 * @param source
	 *            my source domain
	 * @param type
	 *            my type
	 * @param transaction
	 *            the transaction that changed
	 */
	public TransactionalEditingDomainEvent(TransactionalEditingDomain source,
			int type, Transaction transaction) {
		super(source);

		this.type = type;
		this.transaction = transaction;
	}

	@Override
	public TransactionalEditingDomain getSource() {
		return (TransactionalEditingDomain) super.getSource();
	}

	/**
	 * Queries the kind of transactional editing event that I signal to
	 * {@link TransactionalEditingDomainListener}s. Each different event type
	 * corresponds to a call-back operation of that interface.
	 * 
	 * @return my type
	 */
	public int getEventType() {
		return type;
	}

	/**
	 * Queries the transaction for which the event signals a change, or
	 * <code>null</code> if the event pertains to the editing domain, itself.
	 * 
	 * @return the subject transaction, or <code>null</code>
	 */
	public Transaction getTransaction() {
		return transaction;
	}

}
