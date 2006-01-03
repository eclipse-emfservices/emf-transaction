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
 * $Id: TXEditingDomain.java,v 1.1 2006/01/03 20:41:55 cdamus Exp $
 */
package org.eclipse.emf.transaction;


import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.edit.domain.EditingDomain;
import org.eclipse.emf.transaction.impl.TXEditingDomainImpl;

/**
 * An extension of the {@link EditingDomain} API that applies transactional
 * semantics to reading and writing the contents of an EMF {@link ResourceSet}.
 * <p>
 * Editing domains can be created in one of two ways:  dynamically, using
 * a {@link Factory} or statically by registration on the
 * <code>org.eclipse.emf.transaction.editingDomains</code> extension point.
 * The latter mechanism is the preferred way to define editing domains that
 * can be shared with other applications.  To create a new editing domain in
 * code, simply invoke the static factory instance:
 * </p>
 * <PRE>
 *     TXEditingDomain domain = TXEditingDomain.Factory.INSTANCE.createEditingDomain();
 *     ResourceSet rset = domain.getResourceSet();
 *
 *     // or, create our own resource set and initialize the domain with it
 *     rset = new MyResourceSetImpl();
 *     domain = TXEditingDomain.Factory.INSTANCE.createEditingDomain(rset);
 * </PRE>
 * <p>
 * To share a named editing domain with other applications, the editing domain
 * registry can be used to obtain domains by ID, creating them if necessary.
 * Editing domain IDs are configured on an extension point providing the factory
 * implementation that the registry uses to initialize them:
 * </p>
 * <pre>
 *     &lt;!-- In the plugin.xml --&gt;
 *     &lt;extension point="org.eclipse.emf.transaction.editingDomains"&gt;
 *     &lt;editingDomain
 *           id="com.example.MyEditingDomain"
 *           factory="com.example.MyEditingDomainFactory"/&gt;
 *     &lt;/extension&gt;
 *
 *     // in code, access the registered editing domain by:
 *
 *     TXEditingDomain myDomain = TXEditingDomain.Registry.INSTANCE.getEditingDomain(
 *             "com.example.MyEditingDomain");
 * </pre>
 * <p>
 * See the {@link org.eclipse.emf.transaction package documentation} for further
 * details of editing domain usage.
 * </p>
 * 
 * @author Christian W. Damus (cdamus)
 * 
 * @see TXCommandStack
 * @see Transaction
 * @see ResourceSetListener
 */
public interface TXEditingDomain
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
	 * @see Registry#add(String, TXEditingDomain)
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
	 * @see Transaction#commit()
	 */
	Object runExclusive(Runnable read) throws InterruptedException;
	
	/**
	 * Temporarily yields access to another read-only transaction.  The
	 * <code>TXEditingDomain</code> supports any number of pseudo-concurrent
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
		Factory INSTANCE = new TXEditingDomainImpl.FactoryImpl();
		
		/**
		 * Creates an editing domain with a default resource set implementation.
		 * 
		 * @return the new editing domain
		 */
		TXEditingDomain createEditingDomain();
		
		/**
		 * Creates a new transactional editing domain on the specified resource
		 * set.  Although it is possible to create multiple editing domains on
		 * the same resource set, this would rarely be useful.
		 * 
		 * @param rset the resource set
		 * 
		 * @return a new editing domain on the supplied resource set
		 */
		TXEditingDomain createEditingDomain(ResourceSet rset);
		
		/**
		 * Obtains the transactional editing domain (if any) that is currently
		 * managing the specified resource set.
		 * 
		 * @param rset a resource set
		 * 
		 * @return its editing domain, or <code>null</code> if it is not managed
		 *     by any <code>TXEditingDomain</code>
		 */
		TXEditingDomain getEditingDomain(ResourceSet rset);
	}
	
	/**
	 * An ID-based registry of shareable {@link TXEditingDomain} instances.
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
		Registry INSTANCE = new TXEditingDomainImpl.RegistryImpl();
		
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
		TXEditingDomain getEditingDomain(String id);
		
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
		 *    {@link TXEditingDomain#getID() ID} is not the registered ID, then it
		 *    is updated to correspond
		 * 
		 * @throws IllegalArgumentException if the specified ID is already registered
		 *    statically on the extension point
		 */
		void add(String id, TXEditingDomain domain);
		
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
		TXEditingDomain remove(String id);
	}
}
