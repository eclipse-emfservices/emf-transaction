/**
 * <copyright>
 *
 * Copyright (c) 2005, 2008 IBM Corporation, Zeligsoft Inc., and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   IBM - Initial API and implementation
 *   Zeligsoft - Bug 245446
 *
 * </copyright>
 *
 * $Id: Transaction.java,v 1.9 2008/12/03 14:48:13 cdamus Exp $
 */
package org.eclipse.emf.transaction;

import java.util.Map;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.transaction.impl.TransactionImpl;
import org.eclipse.emf.transaction.internal.AllowChangePropagationBlockingOption;
import org.eclipse.emf.transaction.internal.BlockChangePropagationOption;
import org.eclipse.emf.transaction.internal.ValidateEditOption;
import org.eclipse.emf.transaction.util.BasicTransactionOptionMetadata;
import org.eclipse.emf.transaction.util.BasicTransactionOptionMetadataRegistry;
import org.eclipse.emf.transaction.util.ValidateEditSupport;


/**
 * Specification of a transaction in a {@link TransactionalEditingDomain}.  All
 * reading and writing of data in a <code>TransactionalEditingDomain</code> is
 * performed in the context of a transaction.
 * <p>
 * This interface is not intended to be implemented by clients.  It is used
 * internally and by frameworks extending this API.  It is mostly of use to
 * {@link ResourceSetListener}s to find out the state of a transaction in the
 * event call-backs.
 * </p>
 *
 * @author Christian W. Damus (cdamus)
 * 
 * @see TransactionalEditingDomain
 * @see TransactionalCommandStack
 * @see ResourceSetListener
 */
public interface Transaction {
	/**
	 * Option to suppress the post-commit event upon completion of the
	 * transaction.  This does not suppress the pre-commit triggers.
	 * <p>
	 * The value is a {@link Boolean}; the default is {@link Boolean#FALSE}.
	 * </p>
	 */
	String OPTION_NO_NOTIFICATIONS = "silent"; //$NON-NLS-1$
	
	/**
	 * Option to suppress the pre-commit event that implements triggers.
	 * This does not suppress the post-commit event.
	 * <p>
	 * The value is a {@link Boolean}; the default is {@link Boolean#FALSE}.
	 * </p>
	 */
	String OPTION_NO_TRIGGERS = "no_triggers"; //$NON-NLS-1$
	
	/**
	 * Option to suppress validation.  Note that it does not suppress triggers,
	 * so a transaction could still roll back on commit if a pre-commit
	 * listener throws.
	 * <p>
	 * The value is a {@link Boolean}; the default is {@link Boolean#FALSE}.
	 * </p>
	 */
	String OPTION_NO_VALIDATION = "no_validation"; //$NON-NLS-1$
	
	/**
	 * Option to suppress undo/redo recording.  This has two effects:  it
	 * prevents rollback of the transaction, as this requires the undo
	 * information.  It also prevents undo/redo of any {@link RecordingCommand}s
	 * executed in the scope of this transaction.
	 * <p>
	 * The value is a {@link Boolean}; the default is {@link Boolean#FALSE}.
	 * </p>
	 */
	String OPTION_NO_UNDO = "no_undo"; //$NON-NLS-1$
	
	/**
	 * Option to enable a read/write transaction in the scope of a (nesting)
	 * read-only transaction.  Because this option deliberately violates the
	 * read-write exclusion mechanism for model integrity, this option also
	 * suppresses undo recording, triggers, and validation.  It does not
	 * suppress post-commit events.
	 * <p>
	 * The value is a {@link Boolean}; the default is {@link Boolean#FALSE}.
	 * </p>
	 */
	String OPTION_UNPROTECTED = "unprotected"; //$NON-NLS-1$
    
    /**
     * An informational option that tags the transaction as a transaction that
     * is performing the undo or redo of a command.
     * <p>
     * The value is a {@link Boolean}; the default is {@link Boolean#FALSE}.
     * </p><p>
     * <b>Note</b> that this option should not be applied by clients of the
     * transaction API.  Clients may only check to see whether a transaction
     * has this option, e.g., in a
     * {@linkplain ResourceSetListener#resourceSetChanged post-commit listener}.
     * </p>
     * 
     * @since 1.1
     */
    String OPTION_IS_UNDO_REDO_TRANSACTION = "is_undo_redo_transaction";  //$NON-NLS-1$
	
    /**
     * <p>
     * Option indicating that a transaction is to validate-edit the resource
     * that it has modified when it commits, and to roll back if any resources
     * are not modifiable.
     * </p><p>
     * The value is either a {@link Boolean} indicating whether to validate or
     * not, or an instance of the {@link ValidateEditSupport} interface that
     * provides a custom validate-edit implementation.
     * </p>
     * 
     * @see ValidateEditSupport
     * @see #OPTION_VALIDATE_EDIT_CONTEXT
     * 
     * @since 1.2
     */
    String OPTION_VALIDATE_EDIT = "validate_edit";  //$NON-NLS-1$
    
    /**
     * The context object to use when validating edit.  This is usually a
     * <tt>org.eclipse.swt.widgets.Shell</tt> providing a UI context for
     * interaction with the user.
     * 
     * @see #OPTION_VALIDATE_EDIT
     * 
     * @since 1.2
     */
    String OPTION_VALIDATE_EDIT_CONTEXT = "validate_edit_context";  //$NON-NLS-1$
    
	/**
	 * Queries the editing domain in which I am transacting.  Note that this
	 * is available also before I start and after I close.
	 * 
	 * @return my editing domain
	 */
	TransactionalEditingDomain getEditingDomain();
	
	/**
	 * My parent transaction, if any.  The thread that owns an editing domain's
	 * active transaction can create nested transactions simply by starting
	 * new ones.  Nested transactions commit differently from top-level
	 * transactions:  although they send
	 * {@link ResourceSetListener#transactionAboutToCommit(ResourceSetChangeEvent) pre-commit}
	 * events, they do not send post-commit events, nor are they validated.
	 * Validation is performed only by the top-level transaction to validate
	 * all changes made in its scope, and only the top-level transaction then
	 * can send the post-commit event.  Nested transactions can roll back their
	 * changes without affecting their parent transactions.
	 * <p>
	 * Transactions can nest as follows:
	 * </p>
	 * <ul>
	 *   <li>read-only transactions can be nested in read-only or
	 *       read/write transactions</li>
	 *   <li>read/write transactions can only be nested in read/write
	 *       transactions</li>
	 * </ul>
	 * @return my parent transaction
	 */
	Transaction getParent();

	/**
	 * Queries the thread that owns me.  Only this thread is allowed to read
	 * or write (in the case of read/write transactions) the editing domain's
	 * resource set while I am open.
	 * 
	 * @return my owning thread
	 */
	Thread getOwner();
	
	/**
	 * Queries whether I am a read-only transaction.  Even my owning thread
	 * is not permitted to make changes to the model if I am read-only.
	 * 
	 * @return <code>true</code> if I am read-only; <code>false</code>, otherwise
	 */
	boolean isReadOnly();
	
	/**
	 * Obtains the special options with which I was created.  The options
	 * (map keys) are defined by the {@link #OPTION_NO_NOTIFICATIONS Transaction}
	 * interface.
	 * 
	 * @return an unmodifiable view of my options
	 */
	Map<?, ?> getOptions();

	/**
	 * Queries whether I am active.  I am active after I have started and
	 * before I have closed (committed or rolled back).
	 * 
	 * @return whether I am active
	 */
	boolean isActive();
	
	/**
	 * Temporarily yields access to another read-only transaction.  The
	 * {@link TransactionalEditingDomain} supports any number of pseudo-concurrent
	 * read-only transactions.  Transactions that are expected to be
	 * long-running should yield frequently, as a task running in a progress
	 * monitor is expected to check for cancellation frequently.  However, there
	 * is a higher cost (in time) associated with yielding, so it should not
	 * be overdone.
	 * <p>
	 * Only read-only transactions may yield, and only the transaction that
	 * is currently active in the editing domain may yield.  The yielding
	 * transaction may be nested, but not within a read/write transaction
	 * at any depth.
	 * </p>
	 * <p>
	 * Upon yielding, some other read-only transaction that is attempting to
	 * start or to return from a yield will take control of the editing domain.
	 * Control is never yielded to a read/write transaction (not even to a
	 * read-only transaction nested in a read/write) because this would
	 * introduce dirty reads (transactions reading uncommitted changes).
	 * If there are no other read-only transactions to receive the transfer of
	 * control, then the call returns immediately.  Otherwise, control is
	 * transferred in FIFO fashion to waiting transactions.
	 * </p>
	 */
	void yield();
	
	/**
	 * Attempts to commit the transaction.  The transaction may only commit if
	 * it is the currently active transaction in the editing domain.  After the
	 * transaction has committed, it is no longer active and cannot be started
	 * again.
	 * <p>
	 * Commit proceeds in three phases:  pre-commit events and triggers,
	 * validation, and the post-commit events.
	 * </p>
	 * <p>
	 * Pre-commit notifications are sent to the editing domain's registered
	 * {@link ResourceSetListener}s to inform them that the transaction is
	 * committing.  If any listener throws a {@link RollbackException}, then
	 * the transaction is rolled back and the exception is propagated to the
	 * caller.  Any trigger commands returned by pre-commit listeners are
	 * executed after all listeners are invoked, in a nested transaction.
	 * This nested transaction, then, follows the commit protocol to send out
	 * pre-commit notifications again.  This process continues until no more
	 * trigger commands are executed or some listener causes rollback.
	 * </p>
	 * <p>
	 * After all pre-commit processing completes, the transaction is validated.
	 * Validation checks all of the notifications received from the model
	 * during the transaction (including any nested transactions, esp. those
	 * that executed triggers).  If the validation yields an error status (or
	 * more severe), then the transaction is rolled back, throwing a
	 * {@link RollbackException} with the validation status.
	 * </p>
	 * <p>
	 * The final phase, if validation passes, is to send out the post-commit
	 * event to the resource set listeners.  This event includes all of the
	 * notifications received during the transaction, including triggers.
	 * Note that, because these listeners can read the model, they may cause
	 * further notifications (by resolving proxies, loading resources, etc.).
	 * Listeners are invoked in a nested read-only transaction, so it will
	 * also commit and send out a post-commit event if necessary with additional
	 * notifications.
	 * </p>
	 * <p>
	 * <b>Note</b> that even a {@link #isReadOnly() read-only} transaction can
	 * roll back.  This should only occur, however, if it is corrupted by a
	 * concurrent modification by another thread, which means that invalid data
	 * could have been read.
	 * </p>
	 * 
	 * @throws RollbackException if a listener or validation caused the
	 *     transaction to roll back instead of committing successfully
	 */
	void commit() throws RollbackException;
	
	/**
	 * Rolls back the transaction, undoing all of the pending model changes.
	 * Once it has rolled back, the transaction is no longer active and cannot
	 * be started again.  No events are sent when the transaction rolls back;
	 * to listeners it appears that nothing ever happened.
	 */
	void rollback();

	/**
	 * Obtains the change description summarizing the changes made to the model
	 * during the execution of the transaction.  The change description must
	 * not be used until after the transaction has successfully committed.
	 * If the transaction rolls back, then it has no change description.
	 * 
	 * @return the change description, or <code>null</code> if the transaction
	 *     rolled back or is still {@link #isActive() active}
	 */
	TransactionChangeDescription getChangeDescription();
	
	/**
	 * Obtains the status of the transaction.  This may provide warning or
	 * or error messages from validation (after I have committed/rolled back) or
	 * other sources, or it might be OK.
	 * <p>
	 * <b>Note</b> that while I am still active, my status is usually OK.
	 * It may not be OK if I have been aborted, in which case I will
	 * roll back on attempt to commit.
	 * </p>
	 * 
	 * @return my status, most interesting after I have closed
	 */
	public IStatus getStatus();
	
	//
	// Nested types
	//

	/**
	 * <p>
	 * An interface that allows clients to query certain meta-data about
	 * transaction options.
	 * </p>
	 * <p>
	 * This interface is not intended to be implemented by clients. Extend the
	 * {@link BasicTransactionOptionMetadata} class, instead.
	 * </p>
	 * 
	 * @noimplement This interface is not intended to be implemented by clients.
	 * @noextend This interface is not intended to be extended by clients.
	 * 
	 * @author Christian W. Damus (cdamus)
	 * 
	 * @since 1.3
	 * 
	 * @see BasicTransactionOptionMetadata
	 * @see Registry
	 */
	interface OptionMetadata {

		/**
		 * Obtains the key of the option that I describe. This is the key that
		 * would be used in the options map of a transaction.
		 * 
		 * @return my option
		 */
		Object getOption();

		/**
		 * <p>
		 * Queries whether the option is a tag, meaning that it adorns a
		 * transaction with client-specific information but that it does not
		 * otherwise affect the semantics (or behaviour) of the transaction.
		 * </p>
		 * <p>
		 * Unrecognized options are assumed to be tags, because a transaction
		 * would not be able to interpret their meaning.
		 * </p>
		 * 
		 * @return <code>true</code> if the option key is a tag option or if it
		 *         is not recognized by this meta-data instance;
		 *         <code>false</code> if it is recognized and is known not to be
		 *         a tag
		 */
		boolean isTag();

		/**
		 * <p>
		 * Queries whether the option is inherited by nested transactions.
		 * </p>
		 * <p>
		 * Unrecognized options are assumed to be inherited.
		 * </p>
		 * 
		 * @return <code>true</code> if the option is inherited or if it is not
		 *         recognized; <code>false</code> if it is not inherited
		 */
		boolean isHereditary();

		/**
		 * <p>
		 * Obtains the type value of an option.
		 * </p>
		 * <p>
		 * The type of an unrecognized option is assumed to be {@link Object}.
		 * </p>
		 * 
		 * @return the default value of the option, or <code>Object</code> if it
		 *         is not known
		 */
		Class<?> getType();

		/**
		 * <p>
		 * Obtains the default value of an option.
		 * </p>
		 * <p>
		 * The default value of an unrecognized option is assumed to be
		 * <code>null</code>.
		 * </p>
		 * 
		 * @return the default value of the option, or <code>null</code> if it
		 *         is not known
		 */
		Object getDefaultValue();

		/**
		 * Gets the value (implicit/default or explicit) of my option in the
		 * specified map.
		 * 
		 * @param options
		 *            an options map
		 * 
		 * @return my value in the map
		 */
		Object getValue(Map<?, ?> options);
		
		/**
		 * Queries whether the specified map has a setting for my option.
		 * 
		 * @param options
		 *            an options map
		 * @return whether it has a setting for my option
		 */
		boolean isSet(Map<?, ?> options);

		/**
		 * Queries whether the specified options maps have the same value of my
		 * option, whether that be implicit or explicit. That is, this method
		 * accounts for default values and such complex cases as the
		 * {@link Transaction#OPTION_VALIDATE_EDIT} in which values of two
		 * different types may mean the same thing.
		 * 
		 * @param options1
		 *            an options map
		 * @param options2
		 *            another options map
		 * 
		 * @return whether the two maps have the same setting of my option
		 */
		boolean sameSetting(Map<?, ?> options1, Map<?, ?> options2);

		/**
		 * Updates the options map of a child transaction to inherit the setting
		 * in a parent transaction, if it is a hereditary option and the child
		 * does not already have a setting for it.
		 * 
		 * @param parentOptions
		 *            the options map to inherit a value from. It is conceivable
		 *            that inheritance of an option may depend on more than one
		 *            option in this parent map
		 * @param childOptions
		 *            the map that is to inherit the option setting
		 * @param force
		 *            whether to inherit the option anyway despite that it is
		 *            not hereditary. This is used for application of default
		 *            options, and can be ignored by the implementor if
		 *            necessary. Also, clients must not use this parameter to
		 *            attempt to override an existing child setting; a
		 *            well-behaved option will not do that
		 */
		void inherit(Map<?, ?> parentOptions, Map<Object, Object> childOptions,
				boolean force);
		
		/**
		 * <p>
		 * A registry of metadata describing transaction options.  The default
		 * implementation of the {@link TransactionalEditingDomain} interface
		 * provides a transaction option registry as an adapter.  Access to the
		 * registry is thread-safe.
		 * </p>
		 * <p>
		 * This interface is not intended to be implemented by clients.
		 * </p>
		 * 
		 * @noimplement This interface is not intended to be implemented by
		 *              clients.
		 * @noextend This interface is not intended to be extended by clients.
		 * 
		 * @author Christian W. Damus (cdamus)
		 * 
		 * @since 1.3
		 */
		interface Registry {

			/**
			 * The shared transaction option metadata registry.
			 */
			Registry INSTANCE = new BasicTransactionOptionMetadataRegistry(null) {

				private static final long serialVersionUID = 1L;

				{
					// the options that we know
					register(BasicTransactionOptionMetadata.newBoolean(
						Transaction.OPTION_NO_NOTIFICATIONS, false));
					register(BasicTransactionOptionMetadata.newBoolean(
						Transaction.OPTION_NO_TRIGGERS, false));
					register(BasicTransactionOptionMetadata.newBoolean(
						Transaction.OPTION_NO_VALIDATION, false));
					register(BasicTransactionOptionMetadata.newBoolean(
						Transaction.OPTION_NO_UNDO, false));
					register(BasicTransactionOptionMetadata.newBoolean(
						Transaction.OPTION_UNPROTECTED, false));
					register(BasicTransactionOptionMetadata.newBoolean(
						Transaction.OPTION_IS_UNDO_REDO_TRANSACTION, false));

					register(new ValidateEditOption());
					register(new BasicTransactionOptionMetadata(
						Transaction.OPTION_VALIDATE_EDIT_CONTEXT));

					register(BasicTransactionOptionMetadata.newBoolean(
						TransactionImpl.OPTION_IS_TRIGGER_TRANSACTION, false));
					register(new AllowChangePropagationBlockingOption());
					register(new BlockChangePropagationOption());
					register(new BasicTransactionOptionMetadata(
						TransactionImpl.OPTION_EXECUTING_COMMAND, true, false,
						Command.class, null));
				}
			};

			/**
			 * Obtains a metadata object describing the specified transaction
			 * option. For unrecognized options, a default meta-data is provided
			 * that gives reasonable answers.
			 * 
			 * @param option
			 *            an option key
			 * @return the option meta-data (never <code>null</code>)
			 */
			Transaction.OptionMetadata getOptionMetadata(Object option);
		}

	}
}
