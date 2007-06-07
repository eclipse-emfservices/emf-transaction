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
 * $Id: ResourceContentTypeFilter.java,v 1.2 2007/06/07 14:25:59 cdamus Exp $
 */
package org.eclipse.emf.transaction;

import java.io.IOException;
import java.io.InputStream;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.notify.impl.AdapterImpl;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.transaction.internal.Tracing;

/**
 * Implementation of the filter that matches on resource content type.
 *
 * @author Christian W. Damus (cdamus)
 */
class ResourceContentTypeFilter extends NotificationFilter {
	private final IContentType contentType;
	
	/**
	 * Initializes me with the content type that I match.
	 * 
	 * @param contentType the unique identifier of the content type of resources
	 *     that I match
	 *     
	 * @throws IllegalArgumentException if the specified content type ID
	 *     is not defined
	 */
	ResourceContentTypeFilter(String contentType) {
		this.contentType = Platform.getContentTypeManager().getContentType(
				contentType);
		
		if (this.contentType == null) {
			IllegalArgumentException exc = new IllegalArgumentException(
					"No such content type: " + contentType); //$NON-NLS-1$
			Tracing.throwing(NotificationFilter.class,
					"createResourceContentTypeFilter", exc); //$NON-NLS-1$
			throw exc;
		}
	}
	
	// Documentation inherited from the method specification
	public boolean matches(Notification notification) {
		boolean result = false;
		IContentType[] actualTypes = getContentTypes(notification);
		
		for (int i = 0; !result && (i < actualTypes.length); i++) {
			result = actualTypes[i].isKindOf(contentType);
		}
		
		return result;
	}
	
	/**
	 * Gets the cached content types of the resource that either is
	 * the notifier of the specified notification or that contains the
	 * notifier.
	 * 
	 * @param notification a notification from a resource or its
	 *     contents
	 * 
	 * @return the resource's content types
	 */
	private IContentType[] getContentTypes(Notification notification) {
		IContentType[] result;
		Object notifier = notification.getNotifier();
		Resource res = null;
		
		if (notifier instanceof EObject) {
			res = ((EObject) notifier).eResource();
		} else if (notifier instanceof Resource) {
			res = (Resource) notifier;
		}
		
		if (res == null) {
			result = new IContentType[0];
		} else {
			result = getContentTypes(res);
		}
		
		return result;
	}
	
	/**
	 * Gets the cached content types of a resource. If the cache misses.
	 * then we compute the content types and cache them.
	 * 
	 * @param res a resource
	 * 
	 * @return its cached content types
	 */
	private IContentType[] getContentTypes(Resource res) {
		class Cache extends AdapterImpl {
			private IContentType[] contentTypes;
			
			Cache(IContentType[] contentTypes) {
				this.contentTypes = contentTypes;
			}
			
			IContentType[] getContentTypes() {
				return contentTypes;
			}
			
			public boolean isAdapterForType(Object type) {
				return type == Cache.class;
			}
			
			public void notifyChanged(Notification msg) {
				if (!msg.isTouch()) {
					// clear the cache
					getTarget().eAdapters().remove(this);
				}
			}
		}
		
		Cache cache = (Cache) EcoreUtil.getAdapter(
				res.eAdapters(), Cache.class);
		
		if (cache == null) {
			cache = new Cache(computeContentTypes(res));
			res.eAdapters().add(cache);
		}
		
		return cache.getContentTypes();
	}
	
	/**
	 * Computes a resource's content types from its content (if
	 * available on disk) and its file name.
	 * 
	 * @param res a resource
	 * 
	 * @return its content types
	 */
	private IContentType[] computeContentTypes(Resource res) {
		IContentType[] result;
		ResourceSet rset = res.getResourceSet();
		
		if (rset == null) {
			// can't get an input stream, and we don't really care
			//   about this resource anyway if it's not in our editing
			//   domain
			result = new IContentType[0];
		} else {
			URI uri = res.getURI();
			
			// assume that the last segment of the URI (sans query) is
			//   a file name.  If it isn't, then content types don't
			//   really apply anyway
			String filename = uri.trimQuery().lastSegment();
			
			try {
				InputStream stream = rset.getURIConverter().createInputStream(
						uri);
				
				try {
					// use the file contents to get the most accurate
					//    content types
					result = Platform.getContentTypeManager().findContentTypesFor(
							stream,
							filename);
				} catch (IOException e) {
					Tracing.catching(getClass(), "getContentTypes", e);  //$NON-NLS-1$
					result = new IContentType[0];
				} finally {
					stream.close();
				}
			} catch (IOException e) {
				// can be a normal condition, when the resource has
				//    never yet been saved (there is no source for an
				//    input stream).  Might also have trouble reading
				//    the input (more rare).
				//    Just guess by the filename
				Tracing.catching(getClass(), "getContentTypes", e);  //$NON-NLS-1$
				result = Platform.getContentTypeManager().findContentTypesFor(
						filename);
			}
		}
		
		return result;
	}
}
