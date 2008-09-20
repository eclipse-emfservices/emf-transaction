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
 *   Zeligsoft - Bugs 177642, 145877
 *
 * </copyright>
 *
 * $Id: TransactionalEditingDomain.java,v 1.7 2008/09/20 21:23:08 cdamus Exp $
 */
package org.eclipse.emf.transaction;


import java.util.Map;

import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.edit.domain.EditingDomain;
import org.eclipse.emf.transaction.impl.InternalTransactionalEditingDomain;
import org.eclipse.emf.transaction.impl.TransactionalEditingDomainImpl;
import org.eclipse.emf.transaction.util.Adaptable;
import org.eclipse.emf.transaction.util.TransactionUtil;

/**
 * An extension of the {@link EditingDomain} API that applies transactional
 * semantics to reading and writing the contents of an EMF {@link ResourceSet}.
 * <p>
 * Editing domains can be created in one of two ways: dynamically, using a
 * {@link Factory} or statically by registration on the
 * <code>org.eclipse.emf.transaction.editingDomains</code> extension point. The
 * latter mechanism is the preferred way to define editing domains that can be
 * shared with other applications. To create a new editing domain in code,
 * simply invoke the static factory instance:
 * </p>
 * 
 * <PRE>
 * TransactionalEditingDomain domain = TransactionalEditingDomain.Factory.INSTANCE
 * 	.createEditingDomain();
 * ResourceSet rset = domain.getResourceSet();
 * // or, create our own resource set and initialize the domain with it
 * rset = new MyResourceSetImpl();
 * domain = TransactionalEditingDomain.Factory.INSTANCE.createEditingDomain(rset);
 * </PRE>
 * <p>
 * To share a named editing domain with other applications, the editing domain
 * registry can be used to obtain domains by ID, creating them if necessary.
 * Editing domain IDs are configured on an extension point providing the factory
 * implementation that the registry uses to initialize them:
 * </p>
 * 
 * <pre>
 *     &lt;!-- In the plugin.xml --&gt;
 *     &lt;extension point=&quot;org.eclipse.emf.transaction.editingDomains&quot;&gt;
 *     &lt;editingDomain
 *           id=&quot;com.example.MyEditingDomain&quot;
 *           factory=&quot;com.example.MyEditingDomainFactory&quot;/&gt;
 *     &lt;/extension&gt;
 *     // in code, access the registered editing domain by:
 *     TransactionalEditingDomain myDomain = TransactionalEditingDomain.Registry.INSTANCE.getEditingDomain(
 *             &quot;com.example.MyEditingDomain&quot;);
 * </pre>
 * <p>
 * See the {@link org.eclipse.emf.transaction package documentation} for further
 * details of editing domain usage.
 * </p>
 * <p>
 * As of the EMF Transaction 1.2 release, editing domains may optionally be
 * {@link Adaptable} to a variety of optional extension interfaces or "facets."
 * It is recommended to implement the <tt>Adaptable</tt> interface and support
 * adaptation to these interfaces to benefit from the services that they offer.
 * </p>
 * <p>
 * As of the EMF Transaction 1.3 release, resource-set listeners may optionally
 * implement a {@linkplain ResourceSetListener.Internal private} interface to be
 * notified when they are
 * {@linkplain #addResourceSetListener(ResourceSetListener) added} to or
 * {@linkplain #removeResourceSetListener(ResourceSetListener) removed} from an
 * editing domain.
 * </p>
 * <p>
 * Also since the 1.3 release, the new optional
 * {@link TransactionalEditingDomain.Lifecycle} interface provides
 * notifications, from editing domains that support this protocol, of
 * transaction and editing-domain
 * {@linkplain TransactionalEditingDomainListener lifecycle changes}.
 * </p>
 * 
 * @author Christian W. Damus (cdamus)
 * 
 * @see TransactionalCommandStack
 * @see Transaction
 * @see ResourceSetListener
 * @see ResourceSetListener.Internal
 * @see TransactionalEditingDomain.Lifecycle
 * @see TransactionalEditingDomainListener
 */
public interface TransactionalEditingDomain
	extends EditingDomain {

	/**
	 * Obtains my unique ID.  This is the ID under which I am registered in
	 * the {@link Registry} (if I am registered).
	 * 
	 * @return my unique identifier
	 * 
	 * @see Registry#getEditingDomain(String)
	 */
	String getID();
	
	/**
	 * Sets my unique ID.  If I am currently registered in the {@link Registry},
	 * then I am re-registered under this new ID.  However, if I am registered
	 * statically on the <code>org.eclipse.emf.transaction.editingDomains</code>
	 * extension point, then my ID cannot be changed.
	 * 
	 * @param id my new unique identifier
	 * 
	 * @throws IllegalArgumentException if I am a statically registered domain
	 * 
	 * @see #getID()
	 * @see Registry#add(String, TransactionalEditingDomain)
	 */
	void setID(String id);
	
	/**
	 * Adds a resource set listener to me, to receive notifications of changes
	 * to the resource set when transactions commit.  This method has no effect
	 * if the specified listeners is already attached to me.
	 * 
	 * @param l a new resource set listener
	 * 
	 * @throws IllegalArgumentException if the listener declares both that it
	 *     wants only pre-commit events and that it wants only post-commit
	 *     events (a logical contradiction)
	 *     
	 * @see ResourceSetListener#isPrecommitOnly()
	 * @see ResourceSetListener#isPostcommitOnly()
	 */
	void addResourceSetListener(ResourceSetListener l);
	
	/**
	 * Removes a resource set listener from me.  This method has no effect if
	 * the listener is not currently attached to me.
	 * 
	 * @param l a resource set listener to remove
	 */
	void removeResourceSetListener(ResourceSetListener l);
	
	/**
	 * Runs an operation that requires exclusive access to my resource set,
	 * for reading.  The specified runnable is executed in a read-only
	 * transaction.  If the runnable implements the {@link RunnableWithResult}
	 * interface, then its result is returned after it completes.  Moreover,
	 * (and this is a very good reason to implement this extension interface),
	 * if the transaction rolls back on commit, then the <code>RunnableWithResult</code>
	 * is provided with the error status indicating this condition.  Even read-only
	 * transactions can roll back when, for example, another thread concurrently
	 * modifies the model (in violation of the transaction protocol), and it is
	 * important to know when corrupted data may have been read.
	 * <p>
	 * <b>Note</b> that this method will block the current thread until
	 * exclusive access to the resource set can be obtained.  However, it is
	 * safe to call this method on the Eclipse UI thread because special
	 * precaution is taken to ensure that liveness is maintained (using
	 * mechanisms built into the Job Manager).
	 * </p><p>
	 * <b>Note</b>: Since the 1.2 release, the
	 * {@link TransactionUtil#runExclusive(TransactionalEditingDomain, RunnableWithResult)}
	 * utility provides type-safe execution of runnables returning results and
	 * should be preferred over this API.
	 * </p>
	 * 
	 * @param read a read-only operation to execute
	 * 
	 * @return the result of the read operation if it is a
	 *    {@link RunnableWithResult} and the transaction did not roll back;
	 *    <code>null</code>, otherwise
	 *    
	 * @throws InterruptedException if the current thread is interrupted while
	 *    waiting for access to the resource set
	 * 
	 * @see TransactionUtil#runExclusive(RunnableWithResult)
	 * @see Transaction#commit()
	 */
	Object runExclusive(Runnable read) throws InterruptedException;
	
	/**
	 * Temporarily yields access to another read-only transaction.  The
	 * <code>TransactionalEditingDomain</code> supports any number of pseudo-concurrent
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
	 * Wraps the specified <code>runnable</code> to give it access to the currently
	 * active transaction.  This is useful for two or more cooperating threads
	 * to share a transaction (read-only or read-write), executing code in
	 * the a <code>runnable</code> on one thread in the context of another
	 * thread's transaction.
	 * <p>
	 * For example, in an Eclipse UI application, this might be used when a
	 * long-running task in a modal context thread needs to synchronously
	 * execute some operation on the UI thread, which operation needs to read
	 * or write the editing domain. e.g.,
	 * </p>
	 * <pre>
	 *     Runnable uiBoundAction = // ...
	 *     Runnable privileged = domain.createPrivilegedRunnable(uiBoundAction);
	 *     Display.syncExec(privileged);
	 * </pre>
	 * <p>
	 * Note that it is <em>critically important</em> that this mechanism only
	 * be used to share a transaction with another thread <em>synchronously</em>.
	 * Or, more generally, during the execution of the privileged runnable, the
	 * thread that originally owned the transaction no longer does, and may not
	 * access the editing domain.  Upon completion of the privileged runnable,
	 * the transaction is returned to its original owner.
	 * </p>
	 * <p>
	 * Also, the resulting runnable may only be executed while the currently
	 * active transaction remains active.  Any attempt to execute the runnable
	 * after this transaction has committed or while a nested transaction is
	 * active will result in an {@link IllegalStateException}.
	 * </p><p>
	 * <b>Note</b>: Since the 1.2 release, the
	 * {@link TransactionUtil#createPrivilegedRunnable(TransactionalEditingDomain, RunnableWithResult)}
	 * utility provides type-safe privileged access for runnables returning
	 * results and should be preferred over this API.
	 * </p>
	 * 
	 * @param <T> the result type of the {@link RunnableWithResult} if such
	 *    is the <tt>read</tt> argument
	 * 
	 * @param runnable a runnable to execute in the context of the active
	 *     transaction, on any thread
	 *     
	 * @return the privileged runnable.  If the wrapped <code>runnable</code>
	 *     is a {@link RunnableWithResult}, then the privileged runnable will
	 *     inherit its result when it completes
	 * 
	 * @throws IllegalStateException on an attempt by a thread that does not
	 *     own the active transaction to create a privileged runnable.  This
	 *     prevents "theft" of transactions by malicious code.  Note also
	 *     that this implies an exception if there is no active transaction at
	 *     the time of this call
	 *     
	 * @see TransactionUtil#createPrivilegedRunnable(TransactionalEditingDomain, RunnableWithResult)
	 */
	RunnableWithResult<?> createPrivilegedRunnable(Runnable runnable);
	
	/**
	 * Disposes of this editing domain and any resources that it has allocated.
	 * Editing domains must be disposed when they are no longer in use, but
	 * only by the client that created them (in case of sharing of editing
	 * domains).
	 * <p>
	 * <b>Note</b> that editing domains registered on the extension point may
	 * not be disposed.
	 * </p>
	 */
	void dispose();

	/**
	 * Interface defining the protocol for creating transactional editing
	 * domains.  Non-shared editing domains can be created by accessing the
	 * static factory {@link #INSTANCE}.  Shared editing domains (registered
	 * on the <code>org.eclipse.emf.transaction.editingDomains</code>
	 * extension point are obtained via the {@link Registry}.
	 *
	 * @author Christian W. Damus (cdamus)
	 */
	interface Factory {
		/**
		 * Static factory instance that can create instances of the default
		 * transactional editing domain implementation.
		 */
		Factory INSTANCE = new TransactionalEditingDomainImpl.FactoryImpl();
		
		/**
		 * Creates an editing domain with a default resource set implementation.
		 * 
		 * @return the new editing domain
		 */
		TransactionalEditingDomain createEditingDomain();
		
		/**
		 * Creates a new transactional editing domain on the specified resource
		 * set.  Although it is possible to create multiple editing domains on
		 * the same resource set, this would rarely be useful.
		 * 
		 * @param rset the resource set
		 * 
		 * @return a new editing domain on the supplied resource set
		 */
		TransactionalEditingDomain createEditingDomain(ResourceSet rset);
		
		/**
		 * Obtains the transactional editing domain (if any) that is currently
		 * managing the specified resource set.
		 * 
		 * @param rset a resource set
		 * 
		 * @return its editing domain, or <code>null</code> if it is not managed
		 *     by any <code>TransactionalEditingDomain</code>
		 */
		TransactionalEditingDomain getEditingDomain(ResourceSet rset);
	}
	
	/**
	 * An ID-based registry of shareable {@link TransactionalEditingDomain} instances.
	 * Although editing domains can be registered in code, the usual means is
	 * to implement the <code>org.eclipse.emf.transaction.editingDomains</code>
	 * extension point.
	 *
	 * @author Christian W. Damus (cdamus)
	 */
	public interface Registry {
		/**
		 * The single static registry instance.
		 */
		Registry INSTANCE = new TransactionalEditingDomainImpl.RegistryImpl();
		
		/**
		 * Obtains the editing domain having the specified ID.  if the specified
		 * domain is registered on the extension point but has not yet been
		 * created, then it is first created (using the designated factory)
		 * and then returned. 
		 * 
		 * @param id the ID to request
		 * 
		 * @return the matching editing domain, or <code>null</code> if it is
		 *     not found and it could not be created from the extension point
		 */
		TransactionalEditingDomain getEditingDomain(String id);
		
		/**
		 * Registers an editing domain under the specified ID.  This will displace
		 * any domain previously registered under this ID.
		 * Note that it is not permitted to replace an editing domain that
		 * was registered statically on the
		 * <code>org.eclipse.emf.transaction.editingDomains</code> extension
		 * point.
		 * 
		 * @param id the domain ID to register
		 * @param domain the domain to register.  If its current
		 *    {@link TransactionalEditingDomain#getID() ID} is not the registered ID, then it
		 *    is updated to correspond
		 * 
		 * @throws IllegalArgumentException if the specified ID is already registered
		 *    statically on the extension point
		 */
		void add(String id, TransactionalEditingDomain domain);
		
		/**
		 * Removes the editing domain matching the specified ID from the
		 * registry.  Note that it is not permitted to remove an ID that
		 * was registered statically on the
		 * <code>org.eclipse.emf.transaction.editingDomains</code> extension
		 * point.
		 * 
		 * @param id the domain ID to deregister
		 * 
		 * @return the editing domain previously registered under this ID,
		 *    or <code>null</code> if none was registered
		 *    
		 * @throws IllegalArgumentException if the specified ID was registered
		 *    statically on the extension point
		 */
		TransactionalEditingDomain remove(String id);
	}
	
	/**
	 * <p>
	 * Adapter interface provided by {@link TransactionalEditingDomain}s that
	 * support the notion of default transaction options.  This allows clients,
	 * usually when initializing an editing domain, to specify options that
	 * will be applied to any read/write {@link Transaction} for which explicit
	 * values are not provided when they are created.
	 * </p><p>
	 * There are no default-defaults:  by default, an editing domain has no
	 * default transaction options.  Default options are only applied to
	 * root-level transactions.  Nested transactions are expected to inherit
	 * them (or not) as appropriate to the implementation of the options,
	 * as usual.
	 * </p><p>
	 * Note that these are applied also to undo/redo transactions and may be
	 * overridden by the options returned by the
	 * {@link InternalTransactionalEditingDomain#getUndoRedoOptions()} method.
	 * Thus, it may be important for an editing domain to use the undo/redo
	 * options to explicitly disable options that may have defaults.
     * </p><p>
     * The {@linkplain TransactionalEditingDomainImpl default editing domain
     * implementation} provides this adapter interface.
	 * </p>
	 * 
	 * @author Christian W. Damus (cdamus)
	 * 
	 * @since 1.2
	 */
	interface DefaultOptions {
	    /**
	     * Obtains a read-only view of the editing domain's default transaction
	     * options.
	     * 
	     * @return my read-only map of transaction options
	     */
	    Map<?, ?> getDefaultTransactionOptions();
	    
	    /**
	     * Sets the default transaction options.  It is probably best to do this
	     * only when configuring a new editing domain, as inconsistent behaviour
	     * may result from changing the options while editing transactions are
	     * in progress.
	     * 
	     * @param options the new options.  The options are copied from the map
	     */
	    void setDefaultTransactionOptions(Map<?, ?> options);
	}

	/**
	 * <p>
	 * Adapter interface provided by {@link TransactionalEditingDomain}s that
	 * support notification of life-cycle events to
	 * {@link TransactionalEditingDomainListener}s.
	 * </p>
	 * <p>
	 * This interface is not intended to be implemented by clients, but by
	 * editing domain providers.
	 * </p>
	 * 
	 * @author Christian W. Damus (cdamus)
	 * 
	 * @since 1.3
	 */
	interface Lifecycle {
		/**
		 * Adds a listener to be notified of editing domain and transaction
		 * life-cycle events.
		 * 
		 * @param l
		 *            a listener to add
		 */
		void addTransactionalEditingDomainListener(
				TransactionalEditingDomainListener l);

		/**
		 * Removes a lif-cycle event listener from the editing domain.
		 * 
		 * @param l
		 *            a listener to remove
		 */
		void removeTransactionalEditingDomainListener(
				TransactionalEditingDomainListener l);
		
	}
}
