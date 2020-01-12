/**
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   IBM - Initial API and implementation
 */
package org.eclipse.emf.transaction.tests;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.examples.extlibrary.Book;
import org.eclipse.emf.examples.extlibrary.EXTLibraryPackage;
import org.eclipse.emf.examples.extlibrary.Writer;
import org.eclipse.emf.transaction.NotificationFilter;
import org.eclipse.emf.transaction.tests.fixtures.TestListener;


/**
 * Tests notification filtering.
 *
 * @author Christian W. Damus (cdamus)
 */
public class NotificationFilterTest extends AbstractTest {
	
	public NotificationFilterTest(String name) {
		super(name);
	}
	
	public static Test suite() {
		return new TestSuite(NotificationFilterTest.class, "Notification Filter Tests"); //$NON-NLS-1$
	}


	/**
	 * Tests that listeners with different filters get the correct notifications.
	 */
	public void test_filteringOnDispatch() {
		try {
			TestListener bookListener = new TestListener(
					NotificationFilter.createFeatureFilter(
							EXTLibraryPackage.eINSTANCE.getBook_Title()));
			domain.addResourceSetListener(bookListener);
			TestListener authorListener = new TestListener(
					NotificationFilter.createNotifierTypeFilter(
							EXTLibraryPackage.eINSTANCE.getWriter()));
			domain.addResourceSetListener(authorListener);
			
			startWriting();
			
			final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
			assertNotNull(book);
			final String oldTitle = book.getTitle();
			
			String newTitle = "New Title"; //$NON-NLS-1$
			Writer newAuthor = (Writer) find("root/level1/Level1 Writer"); //$NON-NLS-1$
			assertNotNull(newAuthor);
			
			book.setTitle(newTitle);
			book.setAuthor(newAuthor);
			
			commit();
			
			assertNotNull(bookListener.postcommit);
			assertEquals(1, bookListener.postcommitNotifications.size());
			Notification notification = bookListener.postcommitNotifications.get(0);
			assertSame(book, notification.getNotifier());
			assertSame(EXTLibraryPackage.eINSTANCE.getBook_Title(), notification.getFeature());
			assertSame(oldTitle, notification.getOldValue());
			assertSame(newTitle, notification.getNewValue());
			
			assertNotNull(authorListener.postcommit);
			assertEquals(1, authorListener.postcommitNotifications.size());
			notification = authorListener.postcommitNotifications.get(0);
			assertSame(newAuthor, notification.getNotifier());
			assertSame(EXTLibraryPackage.eINSTANCE.getWriter_Books(), notification.getFeature());
			assertSame(book, notification.getNewValue());
		} catch (Exception e) {
			fail(e);
		}
	}
	
	/**
	 * Tests the resource content type filter, filtering for the most specific
	 * content type available.
	 */
	public void test_contentTypeFilter_specific() {
		TestListener listener = new TestListener(
				NotificationFilter.createResourceContentTypeFilter(
						"org.eclipse.emf.examples.library.extendedLibrary")); //$NON-NLS-1$
		
		domain.addResourceSetListener(listener);
		
		startWriting();
		
		// generate a touch notification from the resource
		testResource.setModified(testResource.isModified());
		
		commit();
		
		assertNotNull(listener.postcommit);
		assertNotNull(listener.postcommitNotifications);
		assertEquals(1, listener.postcommitNotifications.size());
		
		listener.reset();

		startWriting();
		
		// generate a touch notification from an object in the resource
		root.setName(root.getName());
		
		commit();
		
		assertNotNull(listener.postcommit);
		assertNotNull(listener.postcommitNotifications);
		assertEquals(1, listener.postcommitNotifications.size());
		
		listener.reset();
		
		domain.removeResourceSetListener(listener);
	}
	
	/**
	 * Tests the resource content type filter, filtering for the a general
	 * content type (not the most specific).
	 */
	public void test_contentTypeFilter_general() {
		TestListener listener = new TestListener(
				NotificationFilter.createResourceContentTypeFilter(
						"org.eclipse.core.runtime.xml")); //$NON-NLS-1$
		
		domain.addResourceSetListener(listener);
		
		startWriting();
		
		// generate a touch notification from the resource
		testResource.setModified(testResource.isModified());
		
		commit();
		
		assertNotNull(listener.postcommit);
		assertNotNull(listener.postcommitNotifications);
		assertEquals(1, listener.postcommitNotifications.size());
		
		listener.reset();

		startWriting();
		
		// generate a touch notification from an object in the resource
		root.setName(root.getName());
		
		commit();
		
		assertNotNull(listener.postcommit);
		assertNotNull(listener.postcommitNotifications);
		assertEquals(1, listener.postcommitNotifications.size());
		
		listener.reset();
		
		domain.removeResourceSetListener(listener);
	}
	
	/**
	 * Tests that the resource content type filter misses for resources that
	 * do not match.
	 */
	public void test_contentTypeFilter_miss() {
		TestListener listener = new TestListener(
				NotificationFilter.createResourceContentTypeFilter(
						"org.eclipse.emf.examples.library.extendedLibrary")); //$NON-NLS-1$
		
		startWriting();
		
		// set the resource to a non-matching file name
		testResource.setURI(
				testResource.getURI().trimFileExtension().appendFileExtension(
						"xml")); //$NON-NLS-1$
		
		domain.addResourceSetListener(listener);
		
		commit();
		
		startWriting();
		
		// generate a touch notification from the resource
		testResource.setModified(testResource.isModified());
		
		commit();
		
		assertNull(listener.postcommit);  // filter did not match
		
		listener.reset();

		startWriting();
		
		// generate a touch notification from an object in the resource
		root.setName(root.getName());
		
		commit();
		
		assertNull(listener.postcommit);  // filter did not match
		
		listener.reset();
		
		domain.removeResourceSetListener(listener);
	}
	
	/**
	 * Tests that the resource content type filter is guessed from the file name
	 * when no content is available to describe (file does not exist).
	 */
	public void test_contentTypeFilter_noContent() {
		TestListener listener = new TestListener(
				NotificationFilter.createResourceContentTypeFilter(
						"org.eclipse.emf.examples.library.extendedLibrary")); //$NON-NLS-1$
		
		startWriting();
		
		// set the resource to a non-existent file name
		testResource.setURI(
				testResource.getURI().trimSegments(1).appendSegment(
						"newname.extlibrary")); //$NON-NLS-1$
		
		domain.addResourceSetListener(listener);
		
		commit();
		
		domain.addResourceSetListener(listener);
		
		startWriting();
		
		// generate a touch notification from the resource
		testResource.setModified(testResource.isModified());
		
		commit();
		
		assertNotNull(listener.postcommit);
		assertNotNull(listener.postcommitNotifications);
		assertEquals(1, listener.postcommitNotifications.size());
		
		listener.reset();

		startWriting();
		
		// generate a touch notification from an object in the resource
		root.setName(root.getName());
		
		commit();
		
		assertNotNull(listener.postcommit);
		assertNotNull(listener.postcommitNotifications);
		assertEquals(1, listener.postcommitNotifications.size());
		
		listener.reset();
		
		domain.removeResourceSetListener(listener);
	}
}
