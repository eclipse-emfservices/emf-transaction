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
 * $Id: TransactionImpl.java,v 1.16 2007/08/23 20:14:15 cdamus Exp $
 */
package org.eclipse.emf.transaction.impl;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.TransactionChangeDescription;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.internal.EMFTransactionDebugOptions;
import org.eclipse.emf.transaction.internal.Tracing;
import org.eclipse.emf.transaction.util.CommandChangeDescription;
import org.eclipse.emf.transaction.util.CompositeChangeDescription;
import org.eclipse.emf.transaction.util.ConditionalRedoCommand;
import org.eclipse.emf.transaction.util.TriggerCommand;

/**
 * The default transaction implementation.
 *
 * @author Christian W. Damus (cdamus)
 */
public class TransactionImpl
	implements InternalTransaction {

	/**
	 * This option, when provided to a transaction that inherits from this implementation
	 *  class and has children transactions that are using this implementation class,
	 *  provides an optional block of the normal propagation of change descriptions
	 *  to the parent transaction by any transaction in the child subtree of this transaction.
	 *  The child exercises its option to negate the propagation of change descriptions by
	 *  adding the {@link #BLOCK_CHANGE_PROPAGATION} option to its own options with
	 *  the value of {@link Boolean#TRUE}. This option <i>IS</i> inherited by child transactions.
	 */
	public static final String ALLOW_CHANGE_PROPAGATION_BLOCKING = "allow_block_cd_prop"; //$NON-NLS-1$
	
	/**
	 * This option blocks the propagation of change descriptions to the parent transaction. The option
	 *  has no effect unless the parent transaction has allowed this negation to happen by having the
	 *  {@link #ALLOW_CHANGE_PROPAGATION_BLOCKING} option added either directly or through
	 *  option inheritance. Note that to enable this option it must be added to the options map with
	 *  the value of {@link Boolean#TRUE}. This option is <i>NOT</i> inherited by child transactions.
	 */
	public static final String BLOCK_CHANGE_PROPAGATION = "block_cd_prop"; //$NON-NLS-1$
	
    /**
     * An informative option that tags the transaction as a transaction that is
     * executing trigger commands.
     */
    public static final String OPTION_IS_TRIGGER_TRANSACTION = "is_trigger_transaction"; //$NON-NLS-1$
    
	/**
	 * The transaction options that should be used when undoing/redoing changes
	 * on the command stack.  Undo and redo must not perform triggers because
	 * these were implemented as chained commands during the original execution.
	 * Moreover, validation is not required during undo/redo because we can
	 * only return the model from a valid state to another valid state if the
	 * original execution did so.  Finally, it is not necessary to record
	 * undo information when we are undoing or redoing.
	 */
	public static final Map DEFAULT_UNDO_REDO_OPTIONS;
	static {
		DEFAULT_UNDO_REDO_OPTIONS = new java.util.HashMap();
		DEFAULT_UNDO_REDO_OPTIONS.put(Transaction.OPTION_NO_TRIGGERS, Boolean.TRUE);
		DEFAULT_UNDO_REDO_OPTIONS.put(Transaction.OPTION_NO_UNDO, Boolean.TRUE);
		DEFAULT_UNDO_REDO_OPTIONS.put(Transaction.OPTION_NO_VALIDATION, Boolean.TRUE);
		DEFAULT_UNDO_REDO_OPTIONS.put(Transaction.OPTION_IS_UNDO_REDO_TRANSACTION, Boolean.TRUE);
	}
	
	private static long nextId = 0L;
	
	final long id;
	
	private final TransactionalEditingDomain domain;
	private Thread owner;
	private final boolean readOnly;
	private final Map options;
	private final Map mutableOptions;
	
	private InternalTransaction parent;
	private InternalTransaction root;
	
	private boolean active;
	private boolean closing; // prevents re-entrant commit/rollback
	private boolean rollingBack;
	protected List notifications;
	protected final CompositeChangeDescription change;
	
	private boolean aborted;
	private IStatus status = Status.OK_STATUS;
	private Command triggers;
	private CommandChangeDescription triggerChange;
	
	/**
	 * Initializes me with my editing domain and read-only state.
	 * 
	 * @param domain the editing domain in which I operate
	 * @param readOnly <code>true</code> if I am read-only; <code>false</code>
	 *     if I am read/write
	 */
	public TransactionImpl(TransactionalEditingDomain domain, boolean readOnly) {
		this(domain, readOnly, null);
	}
	
	/**
	 * Initializes me with my editing domain, read-only state, and additional
	 * options.
	 * 
	 * @param domain the editing domain in which I operate
	 * @param readOnly <code>true</code> if I am read-only; <code>false</code>
	 *     if I am read/write
	 * @param options my options, or <code>null</code> for defaults
	 */
	public TransactionImpl(TransactionalEditingDomain domain, boolean readOnly, Map options) {
		this.domain = domain;
		this.readOnly = readOnly;
		this.owner = Thread.currentThread();
		
		this.mutableOptions = new java.util.HashMap();
		this.options = Collections.unmodifiableMap(mutableOptions);
		
		if (options != null) {
			mutableOptions.putAll(options);
		}
		
		synchronized (TransactionImpl.class) {
			this.id = nextId++;
		}
		
		change = new CompositeChangeDescription();
		
		if (collectsNotifications(this)) {
			notifications = new org.eclipse.emf.common.util.BasicEList.FastCompare();
		} else {
			// no need to collect any notifications if we won't use them
			notifications = null;
		}
	}

	
	// Documentation copied from the inherited specification
	public synchronized void start() throws InterruptedException {
		if (Thread.currentThread() != getOwner()) {
			IllegalStateException exc = new IllegalStateException("Not transaction owner"); //$NON-NLS-1$
			Tracing.throwing(TransactionImpl.class, "start", exc); //$NON-NLS-1$
			throw exc;
		}
		
		if (isActive()) {
			IllegalStateException exc = new IllegalStateException("Transaction is already active"); //$NON-NLS-1$
			Tracing.throwing(TransactionImpl.class, "start", exc); //$NON-NLS-1$
			throw exc;
		}
		
		getInternalDomain().activate(this);
		active = true;

		if (this != getInternalDomain().getActiveTransaction()) {
			IllegalStateException exc = new IllegalStateException("Activated transaction while another is active"); //$NON-NLS-1$
			Tracing.throwing(TransactionImpl.class, "start", exc); //$NON-NLS-1$
			throw exc;
		}
		
		if (Tracing.shouldTrace(EMFTransactionDebugOptions.TRANSACTIONS)) {
			int depth = 1;
			for (Transaction tx = getParent(); tx != null; tx = tx.getParent()) depth++;
				
			Tracing.trace("*** Started " + TransactionalEditingDomainImpl.getDebugID(this) //$NON-NLS-1$
				+ " read-only=" + isReadOnly() //$NON-NLS-1$
				+ " owner=" + getOwner().getName() //$NON-NLS-1$
				+ " depth=" + depth //$NON-NLS-1$
				+ " options=" + getOptions() //$NON-NLS-1$
				+ " at " + Tracing.now()); //$NON-NLS-1$
		}
		
		// do this after activation, because I only have a parent once I have
		//    been activated
		if (parent != null) {
			// the parent stops recording here; I record for myself to implement
			//    support rollback of my changes only
			parent.pause();
		}
		
		startRecording();
	}
	
	// Documentation copied from the inherited specification
	public final TransactionalEditingDomain getEditingDomain() {
		return domain;
	}

	// Documentation copied from the inherited specification
	public final Transaction getParent() {
		return parent;
	}
	
	// Documentation copied from the inherited specification
	public final void setParent(InternalTransaction parent) {
		this.parent = parent;
		
		if (parent == null) {
		    this.root = this;
		} else {
		    this.root = parent.getRoot();
		    inheritOptions(parent);
		}
	}
	
	// Documentation copied from the inherited specification
	public final InternalTransaction getRoot() {
		return root;
	}

	// Documentation copied from the inherited specification
	public final Thread getOwner() {
		return owner;
	}

	// Documentation copied from the inherited specification
	public final boolean isReadOnly() {
		return readOnly;
	}

	// Documentation copied from the inherited specification
	public final Map getOptions() {
		return options;
	}

	// Documentation copied from the inherited specification
	public synchronized boolean isActive() {
		return active;
	}
	
	// Documentation copied from the inherited specification
	public IStatus getStatus() {
		return status;
	}
	
	// Documentation copied from the inherited specification
	public void setStatus(IStatus status) {
		if (status == null) {
			status = Status.OK_STATUS;
		}
		
		this.status = status;
	}
	
	// Documentation copied from the inherited specification
	public synchronized void abort(IStatus status) {
		assert status != null;
		
		this.aborted = true;
		this.status = status;
		
		if (parent != null) {
			// propagate
			parent.abort(status);
		}
	}
	
	/**
	 * Queries whether I have been aborted.
	 * 
	 * @return <code>true</code> if I have been aborted; <code>false</code>, otherwise
	 * 
	 * @see InternalTransaction#abort(IStatus)
	 */
	protected boolean isAborted() {
		return aborted;
	}

	// Documentation copied from the inherited specification
	public void commit() throws RollbackException {
		if (Thread.currentThread() != getOwner()) {
			IllegalStateException exc = new IllegalStateException("Not transaction owner"); //$NON-NLS-1$
			Tracing.throwing(TransactionImpl.class, "commit", exc); //$NON-NLS-1$
			throw exc;
		}
		
		if (closing) {
			IllegalStateException exc = new IllegalStateException("Transaction is already closing"); //$NON-NLS-1$
			Tracing.throwing(TransactionImpl.class, "commit", exc); //$NON-NLS-1$
			throw exc;
		}
		
		if (!isActive()) {
			IllegalStateException exc = new IllegalStateException("Transaction is already closed"); //$NON-NLS-1$
			Tracing.throwing(TransactionImpl.class, "commit", exc); //$NON-NLS-1$
			throw exc;
		}
		
		if (Tracing.shouldTrace(EMFTransactionDebugOptions.TRANSACTIONS)) {
			Tracing.trace("*** Committing " + TransactionalEditingDomainImpl.getDebugID(this) + " at " + Tracing.now()); //$NON-NLS-1$ //$NON-NLS-2$
		}
		
		try {
			// first, check whether I have been aborted.  If so, then I must roll back
			if (isAborted()) {
				closing = false;
				rollback();
				RollbackException exc = new RollbackException(getStatus());
				Tracing.throwing(TransactionImpl.class, "commit", exc); //$NON-NLS-1$
				throw exc;
			}
			
			closing = true;
			
			if (isTriggerEnabled(this)) {
				try {
					getInternalDomain().precommit(this);
				} catch (RollbackException e) {
					Tracing.catching(TransactionImpl.class, "commit", e); //$NON-NLS-1$
					closing = false;  // rollback checks this flag
					rollback();
					Tracing.throwing(TransactionImpl.class, "commit", e); //$NON-NLS-1$
					throw e;
				}
			}
			
			if ((getRoot() == this) && isValidationEnabled(this)) {
				// only the root validates
				IStatus validationStatus = validate();
				setStatus(validationStatus);
				
				if (validationStatus.getSeverity() >= IStatus.ERROR) {
					closing = false;  // rollback checks this flag
					rollback();
					RollbackException exc = new RollbackException(validationStatus);
					Tracing.throwing(TransactionImpl.class, "commit", exc); //$NON-NLS-1$
					throw exc;
				}
			}
		} finally {
			// in case of exception, rollback() already stopped recording
			stopRecording();
			
			close();
		}
	}

	// Documentation copied from the inherited specification
	public void rollback() {
		if (Thread.currentThread() != getOwner()) {
			IllegalStateException exc = new IllegalStateException("Not transaction owner"); //$NON-NLS-1$
			Tracing.throwing(TransactionImpl.class, "rollback", exc); //$NON-NLS-1$
			throw exc;
		}
		
		if (closing) {
			IllegalStateException exc = new IllegalStateException("Transaction is already closing"); //$NON-NLS-1$
			Tracing.throwing(TransactionImpl.class, "rollback", exc); //$NON-NLS-1$
			throw exc;
		}
		
		if (!isActive()) {
			IllegalStateException exc = new IllegalStateException("Transaction is already closed"); //$NON-NLS-1$
			Tracing.throwing(TransactionImpl.class, "rollback", exc); //$NON-NLS-1$
			throw exc;
		}
		
		closing = true;
		rollingBack = true;
		
		if (Tracing.shouldTrace(EMFTransactionDebugOptions.TRANSACTIONS)) {
			Tracing.trace("*** Rolling back " + TransactionalEditingDomainImpl.getDebugID(this) + " at " + Tracing.now()); //$NON-NLS-1$ //$NON-NLS-2$
		}
		
		try {
			if (!isReadOnly()) {
				// ensure that validation of a nesting transaction does not
				//   include any of my changes that I have rolled back and that
				//   post-commit doesn't find any of my changes, either.  Do
				//   this now (before deactivation) so that the validator can
				//   see that I am rolling back
				getInternalDomain().getValidator().remove(this);
				notifications = null;
				
				stopRecording();
				
				if (isUndoEnabled(this)) {
					change.apply();
					
					// forget the description.  The changes are reverted
					change.clear();
				}
			}
		} finally {
			rollingBack = false;
			close();
		}
	}

	// Documentation copied from the inherited specification
	public void yield() {
		getEditingDomain().yield();
	}

	// Documentation copied from the inherited specification
	public TransactionChangeDescription getChangeDescription() {
		return (isActive() && !closing) ? null : change;
	}

	/**
	 * Obtains my owning editing domain as the internal interface.
	 * 
	 * @return the internal view of my editing domain
	 */
	protected InternalTransactionalEditingDomain getInternalDomain() {
		return (InternalTransactionalEditingDomain) getEditingDomain();
	}

	/**
	 * Starts recording changes upong activation or resumption from a child
	 * transaction, unless undo recording is disabled by my options.
	 */
	private void startRecording() {
		TransactionChangeRecorder recorder = getInternalDomain().getChangeRecorder();
		
		if (isUndoEnabled(this)) {
			if (!recorder.isRecording()) {
				recorder.beginRecording();
			} else if (recorder.isPaused()) {
				recorder.resume();
			}
		}
	}
	
	/**
	 * Stops recording changes and adds them to my composite change description,
	 * unless undo recording is disabled by my options.
	 */
	private void stopRecording() {
		TransactionChangeRecorder recorder = getInternalDomain().getChangeRecorder();
		
		if (isUndoEnabled(this) && recorder.isRecording()) {
			Transaction active = getInternalDomain().getActiveTransaction();
			if ((active != null) && !isUndoEnabled(active)) {
				// the child is not recording, so we just suspend our change
				//    recording and resume it later.  This is a lightweight
				//    alternative to cutting a change description and starting
				//    a new one, later
				recorder.pause();
			} else {
				change.add(recorder.endRecording());
			}
		}
	}
	
	// Documentation copied from the inherited specification
	public void pause() {
		// if we are rolling back, then we don't need to worry about recording
		//    changes because we are permanently undoing changes.
		//    See additional comments in the resume(TransactionChangeDescription)
		//    method
		if (!isRollingBack()) {
			stopRecording();
		}
	}
	
	// Documentation copied from the inherited specification
	public void resume(TransactionChangeDescription nestedChanges) {
		// if we are rolling back, then we don't need to worry about recording
		//    changes because we are permanently undoing changes.  It can happen
		//    that a nested transaction is created in order to roll back changes
		//    that are nested within non-EMF changes, which would cause a
		//    concurrent modification of our composite change if we were to add
		//    the nested transaction's changes to us
		if (!isRollingBack()) {
			if (isUndoEnabled(this) && (nestedChanges != null)) {
				change.add(nestedChanges);
			}
			
			startRecording();
		}
	}
	
	// Documentation copied from the inherited specification
	public boolean isRollingBack() {
		return rollingBack || ((parent != null) && parent.isRollingBack());
	}

	/**
	 * Closes me.  This is the last step in committing or rolling back,
	 * deactivating me in my editing domain.  Also, if I have a parent
	 * transaction, I {@link InternalTransaction#resume(TransactionChangeDescription) resume}
	 * it.
	 * <p>
	 * If a subclass overrides this method, it <em>must</em> ensure that this
	 * implementation is also invoked.
	 * </p>
	 */
	protected synchronized void close() {
		if (isActive()) {
			active = false;
			closing = false;
			getInternalDomain().deactivate(this);
			
			if (parent != null) {
				// my parent resumes recording its changes now that mine are either
				//  committed to it or rolled back. The parent accumulates
				//  my changes except for certain special cases where we must
				//  prevent the passing of our changes.
				
				if (getOptions().get(BLOCK_CHANGE_PROPAGATION) == Boolean.TRUE
						&& parent.getOptions().get(ALLOW_CHANGE_PROPAGATION_BLOCKING) == Boolean.TRUE) {
					parent.resume(null);
				} else {
					parent.resume(change);
				}
			} else {
				// I am a root transaction.  Forget my notifications, if any
				notifications = null;
			}
			
			if (Tracing.shouldTrace(EMFTransactionDebugOptions.TRANSACTIONS)) {
				Tracing.trace("*** Closed " + TransactionalEditingDomainImpl.getDebugID(this) + " at " + Tracing.now()); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
	}
	
	// Documentation copied from the inherited specification
	public void add(Notification notification) {
		if (!rollingBack && (notifications != null)) {
			notifications.add(notification);
		}
	}
	
	// Documentation copied from the inherited specification
	public List getNotifications() {
		return (notifications == null)? Collections.EMPTY_LIST : notifications;
	}
	
	/**
	 * Validates me.  Should only be called during commit.
	 * 
	 * @return the result of validation.  If this is an error or worse,
	 *     then I must roll back
	 */
	protected IStatus validate() {
		if (Tracing.shouldTrace(EMFTransactionDebugOptions.TRANSACTIONS)) {
			Tracing.trace("*** Validating " + TransactionalEditingDomainImpl.getDebugID(this) + " at " + Tracing.now()); //$NON-NLS-1$ //$NON-NLS-2$
		}
		
		return getInternalDomain().getValidator().validate(this);
	}
	
	// Documentation copied from the inherited specification
	public Command getTriggers() {
		return triggers;
	}

	// Documentation copied from the inherited specification
	public void addTriggers(TriggerCommand triggers) {
		// extract out only the triggered commands (exclude the triggering command)
		List triggerCommands = triggers.getTriggers();
		Command triggerCommand;
		
		if (triggerCommands.isEmpty()) {
			return;
		}
		
		// bug 165026:  Must copy the list, because it may be read-only like
		//    GMF's NOOP_TRIGGER command
		triggerCommand = new ConditionalRedoCommand.Compound(
				new java.util.ArrayList(triggerCommands));
		
		if (this.triggers == null) {
			this.triggers = triggerCommand;
			
			// record the triggers in my change description
			triggerChange = new CommandChangeDescription(triggerCommand);
			change.add(triggerChange);
		} else {
			this.triggers = triggerChange.chain(triggerCommand);
		}
	}
	
	// Documentation copied from the inherited specification
	public void startPrivileged(PrivilegedRunnable runnable) {
		if (runnable.getTransaction() != this) {
			throw new IllegalArgumentException(
					"runnable has no privileges on this transaction"); //$NON-NLS-1$
		}
		
		InternalTransactionalEditingDomain internalDomain =
			(InternalTransactionalEditingDomain) getEditingDomain();
		
		if (!isActive() || (internalDomain.getActiveTransaction() != this)) {
			throw new IllegalStateException(
					"transaction is not the domain's current transaction"); //$NON-NLS-1$
		}
		
		internalDomain.startPrivileged(runnable);
		
		owner = Thread.currentThread();
	}

	// Documentation copied from the inherited specification
	public void endPrivileged(PrivilegedRunnable runnable) {
		if (runnable.getTransaction() != this) {
			throw new IllegalArgumentException(
					"runnable has no privileges on this transaction"); //$NON-NLS-1$
		}
		
		InternalTransactionalEditingDomain internalDomain =
			(InternalTransactionalEditingDomain) getEditingDomain();
		
		if (!isActive() || (internalDomain.getActiveTransaction() != this)) {
			throw new IllegalStateException(
					"transaction is not the domain's current transaction"); //$NON-NLS-1$
		}
		
		owner = runnable.getOwner();
		
		internalDomain.endPrivileged(runnable);
	}
	
	private void inheritOptions(Transaction parent) {
        
        Map parentOptions = (parent == null)? null : parent.getOptions();
        
        if (parentOptions != null) {
            for (Iterator iter = parentOptions.entrySet().iterator(); iter.hasNext();) {
                Map.Entry next = (Map.Entry) iter.next();
                Object option = next.getKey();
                
                if (ALLOW_CHANGE_PROPAGATION_BLOCKING.equals(option)) {
                    // Do not inherit the allow block option if we have the
                    // block option applied
                    if (!mutableOptions.containsKey(BLOCK_CHANGE_PROPAGATION)) {
                        mutableOptions.put(option, next.getValue());
                    }
                } else if (BLOCK_CHANGE_PROPAGATION.equals(option)) {
                    // don't inherit the block option!
                } else if (!mutableOptions.containsKey(option)) {
                    // overridden by child transaction options
                    mutableOptions.put(option, next.getValue());
                }
            }            
        }
	}
	
	public String toString() {
		return "Transaction[active=" + isActive() //$NON-NLS-1$
			+ ", read-only=" + isReadOnly() //$NON-NLS-1$
			+ ", owner=" + getOwner().getName() + ']'; //$NON-NLS-1$
	}
	
	/**
	 * Queries whether the specified transaction should record undo information,
	 * according to its {@link Transaction#getOptions() options} and
	 * {@link Transaction#isReadOnly() read-only state}.
	 * 
	 * @param tx a transaction
	 * @return <code>true</code> if the transaction should record undo
	 *     information; <code>false</code>, otherwise
	 */
	protected static boolean isUndoEnabled(Transaction tx) {
		return !(tx.isReadOnly()
				|| hasOption(tx, OPTION_NO_UNDO)
				|| hasOption(tx, OPTION_UNPROTECTED));
	}
	
	/**
	 * Queries whether the specified transaction should validate changes,
	 * according to its {@link Transaction#getOptions() options} and
	 * {@link Transaction#isReadOnly() read-only state}.
	 * 
	 * @param tx a transaction
	 * @return <code>true</code> if the transaction should validate
	 *     changes; <code>false</code>, otherwise
	 */
	protected static boolean isValidationEnabled(Transaction tx) {
		return !(tx.isReadOnly()
				|| hasOption(tx, OPTION_NO_VALIDATION)
				|| hasOption(tx, OPTION_UNPROTECTED));
	}
	
	/**
	 * Queries whether the specified transaction should invoke pre-commit,
	 * listeners, according to its {@link Transaction#getOptions() options} and
	 * {@link Transaction#isReadOnly() read-only state}.
	 * 
	 * @param tx a transaction
	 * @return <code>true</code> if the transaction should perform the pre-commit
	 *     procedures; <code>false</code>, otherwise
	 */
	protected static boolean isTriggerEnabled(Transaction tx) {
		return !(tx.isReadOnly()
				|| hasOption(tx, OPTION_NO_TRIGGERS)
				|| hasOption(tx, OPTION_UNPROTECTED));
	}
	
	/**
	 * Queries whether the specified transaction should send post-commit events,
	 * according to its {@link Transaction#getOptions() options}.
	 * 
	 * @param tx a transaction
	 * @return <code>true</code> if the transaction should send post-commit
	 *     events; <code>false</code>, otherwise
	 */
	protected static boolean isNotificationEnabled(Transaction tx) {
		return !hasOption(tx, OPTION_NO_NOTIFICATIONS);
	}
	
	/**
	 * Queries whether the specified transaction is an unprotected write,
	 * according to its {@link Transaction#getOptions() options} and
	 * {@link Transaction#isReadOnly() read-only state}.
	 * 
	 * @param tx a transaction
	 * @return <code>true</code> if the transaction is an unprotected write
	 *     transaction; <code>false</code>, otherwise
	 */
	protected static boolean isUnprotected(Transaction tx) {
		return !tx.isReadOnly()
				&& hasOption(tx, OPTION_UNPROTECTED);
	}
	
	/**
	 * Queries whether the specified transaction collects notifications for
	 * broadcast to listeners or for validation.  This is determined by
	 * the transaction's options.
	 * 
	 * @param tx a transaction
	 * 
	 * @return <code>true</code> any of notification, triggers, and validation
	 *     are enabled; <code>false</code>, otherwise
	 * 
	 * @see #isNotificationEnabled(Transaction)
	 * @see #isTriggerEnabled(Transaction)
	 * @see #isValidationEnabled(Transaction)
	 */
	protected static boolean collectsNotifications(Transaction tx) {
		return isNotificationEnabled(tx)
			|| isTriggerEnabled(tx)
			|| isValidationEnabled(tx);
	}
	
	/**
	 * Queries whether the specified transaction has a boolean option.
	 * 
	 * @param tx a transaction
	 * @param option the boolean-valued option to query
	 * @return <code>true</code> if the transaction has the option;
	 *    <code>false</code> if it does not
	 */
	protected static boolean hasOption(Transaction tx, String option) {
		return Boolean.TRUE.equals(tx.getOptions().get(option));
	}
}
