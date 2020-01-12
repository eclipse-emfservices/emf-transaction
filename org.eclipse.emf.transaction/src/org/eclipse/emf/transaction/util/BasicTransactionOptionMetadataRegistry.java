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
package org.eclipse.emf.transaction.util;

import org.eclipse.emf.transaction.Transaction;

/**
 * A simple implementation of the transaction option metadata registry API.
 * 
 * @author Christian W. Damus (cdamus)
 * 
 * @since 1.3
 */
public class BasicTransactionOptionMetadataRegistry
		extends
		java.util.concurrent.ConcurrentHashMap<Object, Transaction.OptionMetadata>
		implements Transaction.OptionMetadata.Registry {

	private static final long serialVersionUID = 1L;

	private final Transaction.OptionMetadata.Registry delegate;

	private final BasicTransactionOptionMetadataRegistry basicDelegate;

	/**
	 * Initializes me with the shared registry instance as my delegate.
	 */
	public BasicTransactionOptionMetadataRegistry() {
		this(Transaction.OptionMetadata.Registry.INSTANCE);
	}

	/**
	 * Initializes me with a registry to which I delegate options that I do not
	 * provide for.
	 * 
	 * @param delegate
	 *            my delegate
	 */
	protected BasicTransactionOptionMetadataRegistry(
			Transaction.OptionMetadata.Registry delegate) {
		super(16, 0.75f, 2);

		this.delegate = delegate;
		this.basicDelegate = (delegate instanceof BasicTransactionOptionMetadataRegistry)
			? (BasicTransactionOptionMetadataRegistry) delegate
			: null;
	}

	/**
	 * <p>
	 * {@inheritDoc}
	 * </p>
	 * <p>
	 * This implementation caches, locally, the lazily created metadata for
	 * options that are unrecognized.
	 * </p>
	 */
	public Transaction.OptionMetadata getOptionMetadata(Object option) {
		Transaction.OptionMetadata result = basicGetOptionMetadata(option);

		if (result == null) {
			// create a default metadata
			result = new BasicTransactionOptionMetadata(option);

			// and cache it, unless somebody else has already done so
			Transaction.OptionMetadata existing = putIfAbsent(option, result);
			if (existing != null) {
				// use the other, instead
				result = existing;
			}
		}

		return result;
	}

	/**
	 * Gets the specified option's metadata, possibly from a delegate, without
	 * lazily creating and caching a result for unrecognized options.
	 * 
	 * @param option
	 *            the option
	 * @return its metadata, or <code>null</code> if none is found in either the
	 *         local or the delegate registry
	 */
	protected Transaction.OptionMetadata basicGetOptionMetadata(Object option) {

		Transaction.OptionMetadata result = get(option);

		if (result == null) {
			result = delegatedGetOptionMetadata(option);
		}

		return result;
	}

	protected Transaction.OptionMetadata delegatedGetOptionMetadata(
			Object option) {

		// don't cache a lazy metadata result in the delegate
		return (basicDelegate != null)
			? basicDelegate.basicGetOptionMetadata(option)
			: (delegate != null)
				? delegate.getOptionMetadata(option)
				: null;
	}

	/**
	 * Registers an option metadata descriptor.
	 * 
	 * @param metadata
	 *            the option metadata to register
	 * @return the metadata displaced by the new object, if previously we had a
	 *         descriptor for the same option, otherwise <code>null</code>
	 */
	public Transaction.OptionMetadata register(
			Transaction.OptionMetadata metadata) {
		return put(metadata.getOption(), metadata);
	}
}
