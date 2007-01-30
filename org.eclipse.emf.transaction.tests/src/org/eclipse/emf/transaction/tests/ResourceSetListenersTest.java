/**
 * <copyright>
 *
 * Copyright (c) 2005, 2006 IBM Corporation and others.
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
 * $Id: ResourceSetListenersTest.java,v 1.7 2007/01/30 22:05:02 cdamus Exp $
 */
package org.eclipse.emf.transaction.tests;

import java.util.Collections;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.edit.command.SetCommand;
import org.eclipse.emf.examples.extlibrary.Book;
import org.eclipse.emf.examples.extlibrary.BookCategory;
import org.eclipse.emf.examples.extlibrary.EXTLibraryFactory;
import org.eclipse.emf.examples.extlibrary.EXTLibraryPackage;
import org.eclipse.emf.examples.extlibrary.Library;
import org.eclipse.emf.transaction.DemultiplexingListener;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.ResourceSetChangeEvent;
import org.eclipse.emf.transaction.ResourceSetListenerImpl;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.tests.fixtures.ItemDefaultPublicationDateTrigger;
import org.eclipse.emf.transaction.tests.fixtures.LibraryDefaultBookTrigger;
import org.eclipse.emf.transaction.tests.fixtures.LibraryDefaultNameTrigger;
import org.eclipse.emf.transaction.tests.fixtures.TestCommand;
import org.eclipse.emf.transaction.tests.fixtures.TestListener;


/**
 * Tests resource listener events.
 *
 * @author Christian W. Damus (cdamus)
 */
public class ResourceSetListenersTest extends AbstractTest {
	
	private TestListener listener;
	
	public ResourceSetListenersTest(String name) {
		super(name);
	}
	
	public static Test suite() {
		return new TestSuite(ResourceSetListenersTest.class, "Resource Set Listener Framework Tests"); //$NON-NLS-1$
	}


	/**
	 * Tests that simple changes are propagated to post-commit listeners.
	 */
	public void test_postcommit() {
		try {
			
			final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
			assertNotNull(book);
			
			String newTitle = "New Title"; //$NON-NLS-1$
			
			Command cmd = new SetCommand(
				domain, book, EXTLibraryPackage.eINSTANCE.getBook_Title(), newTitle);
			
			domain.getCommandStack().execute(cmd);
			
			assertNotNull(listener.postcommit);
			assertNotNull(listener.postcommit.getTransaction());
			assertFalse(listener.postcommit.getTransaction().isActive());
			assertSame(domain, listener.postcommit.getEditingDomain());
			
			List notifications = listener.postcommitNotifications;
			assertNotNull(notifications);
			assertEquals(1, notifications.size());
			
			Notification notification = (Notification) notifications.get(0);
			assertSame(book, notification.getNotifier());
			assertSame(EXTLibraryPackage.eINSTANCE.getBook_Title(), notification.getFeature());
			assertSame(newTitle, notification.getNewValue());
		} catch (Exception e) {
			fail(e);
		}
	}

	/**
	 * Tests that changes from nested transactions are propagated to post-commit
	 * listeners and that nested transactions do not fire postcommit.
	 */
	public void test_postcommit_nestedChange() {
		try {
			startReading();
			
			final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
			
			commit();
			
			assertNotNull(book);
			
			final String newTitle = "New Title"; //$NON-NLS-1$
			
			Command cmd = new TestCommand() {
			
				public void execute() {
					try {
						// nested transaction
						startWriting();
						
						book.setTitle(newTitle);
						
						commit();
						
						assertNull(listener.postcommit);
					} catch (Exception e) {
						fail(e);
					}
				}
			
			};
			
			domain.getCommandStack().execute(cmd);
			
			assertNotNull(listener.postcommit);
			assertNotNull(listener.postcommit.getTransaction());
			assertFalse(listener.postcommit.getTransaction().isActive());
			assertSame(domain, listener.postcommit.getEditingDomain());
			
			List notifications = listener.postcommitNotifications;
			assertNotNull(notifications);
			assertEquals(1, notifications.size());
			
			Notification notification = (Notification) notifications.get(0);
			assertSame(book, notification.getNotifier());
			assertSame(EXTLibraryPackage.eINSTANCE.getBook_Title(), notification.getFeature());
			assertSame(newTitle, notification.getNewValue());
		} catch (Exception e) {
			fail(e);
		}
	}

	/**
	 * Tests that changes from nested transactions are propagated to post-commit
	 * listeners in the correct thread-serial order.
	 */
	public void test_postcommit_ordering() {
		try {
			startReading();
			
			final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
			assertNotNull(book);
			
			commit();
			
			final String newTitle = "New Title"; //$NON-NLS-1$
			
			Command cmd = new TestCommand() {
			
				public void execute() {
					try {
						book.setCategory(BookCategory.BIOGRAPHY_LITERAL);
						
						// nested transaction
						startWriting();
						
						book.setTitle(newTitle);
						
						commit();
						
						book.setPages(500);
					} catch (Exception e) {
						fail(e);
					}
				}
			
			};
			
			domain.getCommandStack().execute(cmd);
			
			assertNotNull(listener.postcommit);
			assertNotNull(listener.postcommit.getTransaction());
			assertFalse(listener.postcommit.getTransaction().isActive());
			assertSame(domain, listener.postcommit.getEditingDomain());
			
			List notifications = listener.postcommitNotifications;
			assertNotNull(notifications);
			assertEquals(3, notifications.size());
			
			Notification notification = (Notification) notifications.get(0);
			assertSame(book, notification.getNotifier());
			assertSame(EXTLibraryPackage.eINSTANCE.getBook_Category(), notification.getFeature());
			assertSame(BookCategory.BIOGRAPHY_LITERAL, notification.getNewValue());
			
			notification = (Notification) notifications.get(1);
			assertSame(book, notification.getNotifier());
			assertSame(EXTLibraryPackage.eINSTANCE.getBook_Title(), notification.getFeature());
			assertSame(newTitle, notification.getNewValue());
			
			notification = (Notification) notifications.get(2);
			assertSame(book, notification.getNotifier());
			assertSame(EXTLibraryPackage.eINSTANCE.getBook_Pages(), notification.getFeature());
			assertEquals(new Integer(500), notification.getNewValue());
		} catch (Exception e) {
			fail(e);
		}
	}

	/**
	 * Tests that post-commit listeners can read, but not write.
	 */
	public void test_postcommit_readOnly() {
		startReading();
		
		final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
		assertNotNull(book);
		
		commit();
		
		TestListener testListener = new TestListener() {
			public void resourceSetChanged(ResourceSetChangeEvent event) {
				try {
					// can execute read operations
					domain.runExclusive(new Runnable() {
						public void run() {
							// do nothing
						}});
				} catch (Exception e) {
					fail(e);
				}
				
				try {
					// cannot execute commands
					domain.getCommandStack().execute(new TestCommand() {
						public void execute() {
							// do nothing
						}});
					
					fail("Should have thrown IllegalStateException"); //$NON-NLS-1$
				} catch (Exception e) {
					// success
					trace("Got expected exception: " + e.getLocalizedMessage()); //$NON-NLS-1$
				}
				
				try {
					// cannot make ad hoc changes
					book.setCategory(BookCategory.BIOGRAPHY_LITERAL);
					
					fail("Should have thrown IllegalStateException"); //$NON-NLS-1$
				} catch (Exception e) {
					// success
					trace("Got expected exception: " + e.getLocalizedMessage()); //$NON-NLS-1$
				}
			}};
		
		try {
			domain.addResourceSetListener(testListener);
			
			String newTitle = "New Title"; //$NON-NLS-1$
			
			Command cmd = new SetCommand(
				domain, book, EXTLibraryPackage.eINSTANCE.getBook_Title(), newTitle);
			
			domain.getCommandStack().execute(cmd);
		} catch (Exception e) {
			fail(e);
		} finally {
			// must remove the listener, otherwise it will be invoked again
			//   by the resource unload and it will fail at that time
			domain.removeResourceSetListener(testListener);
		}
	}

	/**
	 * Tests that simple changes are propagated to pre-commit listeners.
	 */
	public void test_precommit() {
		try {
			startReading();
			
			final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
			assertNotNull(book);
			
			commit();
			
			String newTitle = "New Title"; //$NON-NLS-1$
			
			Command cmd = new SetCommand(
				domain, book, EXTLibraryPackage.eINSTANCE.getBook_Title(), newTitle);
			
			domain.getCommandStack().execute(cmd);
			
			assertNotNull(listener.precommit);
			assertNotNull(listener.precommit.getTransaction());
			assertFalse(listener.precommit.getTransaction().isActive());
			assertSame(domain, listener.precommit.getEditingDomain());
			
			List notifications = listener.precommitNotifications;
			assertNotNull(notifications);
			assertEquals(1, notifications.size());
			
			Notification notification = (Notification) notifications.get(0);
			assertSame(book, notification.getNotifier());
			assertSame(EXTLibraryPackage.eINSTANCE.getBook_Title(), notification.getFeature());
			assertSame(newTitle, notification.getNewValue());
		} catch (Exception e) {
			fail(e);
		}
	}

	/**
	 * Tests that changes from nested transactions are not propagated to pre-commit
	 * listeners by the outer transaction.
	 */
	public void test_precommit_nestedChange() {
		try {
			startReading();
			
			final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
			assertNotNull(book);
			
			commit();
			
			final String newTitle = "New Title"; //$NON-NLS-1$
			
			Command cmd = new TestCommand() {
			
				public void execute() {
					try {
						// nested transaction
						startWriting();
						
						book.setTitle(newTitle);
						
						commit();
						
						// reset the listener to the outer commit
						listener.reset();
					} catch (Exception e) {
						fail(e);
					}
				}
			
			};
			
			domain.getCommandStack().execute(cmd);
			
			assertNull(listener.precommit);
		} catch (Exception e) {
			fail(e);
		}
	}

	/**
	 * Tests that changes from nested transactions are not propagated to pre-commit
	 * listeners, but not including previous changes of the outer transaction.
	 */
	public void test_precommit_nestedChange2() {
		try {
			startReading();
			
			final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
			assertNotNull(book);
			
			commit();
			
			final String newTitle = "New Title"; //$NON-NLS-1$
			
			Command cmd = new TestCommand() {
			
				public void execute() {
					try {
						book.setCategory(BookCategory.BIOGRAPHY_LITERAL);
						
						// nested transaction
						startWriting();
						
						book.setTitle(newTitle);
						
						commit();
						
						assertNotNull(listener.precommit);
						assertNotNull(listener.precommit.getTransaction());
						assertFalse(listener.precommit.getTransaction().isActive());
						assertSame(domain, listener.precommit.getEditingDomain());
						
						List notifications = listener.precommitNotifications;
						assertNotNull(notifications);
						assertEquals(1, notifications.size());
						
						Notification notification = (Notification) notifications.get(0);
						assertSame(book, notification.getNotifier());
						assertSame(EXTLibraryPackage.eINSTANCE.getBook_Title(), notification.getFeature());
						assertSame(newTitle, notification.getNewValue());
						
						// reset the listener to the outer commit
						listener.reset();
					} catch (Exception e) {
						fail(e);
					}
				}
			
			};
			
			domain.getCommandStack().execute(cmd);
			
			assertNotNull(listener.precommit);
			assertNotNull(listener.precommit.getTransaction());
			assertFalse(listener.precommit.getTransaction().isActive());
			assertSame(domain, listener.precommit.getEditingDomain());
			
			List notifications = listener.precommitNotifications;
			assertNotNull(notifications);
			assertEquals(1, notifications.size());
			
			Notification notification = (Notification) notifications.get(0);
			assertSame(book, notification.getNotifier());
			assertSame(EXTLibraryPackage.eINSTANCE.getBook_Category(), notification.getFeature());
			assertSame(BookCategory.BIOGRAPHY_LITERAL, notification.getNewValue());
		} catch (Exception e) {
			fail(e);
		}
	}

	/**
	 * Tests that pre-commit listeners can read, but not write.
	 */
	public void test_precommit_readOnly() {
		startReading();
		
		final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
		assertNotNull(book);
		
		commit();
		
		TestListener testListener = new TestListener() {
			public Command transactionAboutToCommit(ResourceSetChangeEvent event) {
				try {
					// can execute read operations
					domain.runExclusive(new Runnable() {
						public void run() {
							// do nothing
						}});
				} catch (Exception e) {
					fail(e);
				}
				
				try {
					// cannot execute commands
					domain.getCommandStack().execute(new TestCommand() {
						public void execute() {
							// do nothing
						}});
					
					fail("Should have thrown IllegalStateException"); //$NON-NLS-1$
				} catch (Exception e) {
					// success
					trace("Got expected exception: " + e.getLocalizedMessage()); //$NON-NLS-1$
				}
				
				try {
					// cannot make ad hoc changes
					book.setCategory(BookCategory.BIOGRAPHY_LITERAL);
					
					fail("Should have thrown IllegalStateException"); //$NON-NLS-1$
				} catch (Exception e) {
					// success
					trace("Got expected exception: " + e.getLocalizedMessage()); //$NON-NLS-1$
				}
				
				return null;
			}};
		
		try {
			domain.addResourceSetListener(testListener);
			
			String newTitle = "New Title"; //$NON-NLS-1$
			
			Command cmd = new SetCommand(
				domain, book, EXTLibraryPackage.eINSTANCE.getBook_Title(), newTitle);
			
			domain.getCommandStack().execute(cmd);
		} catch (Exception e) {
			fail(e);
		} finally {
			// must remove the listener, otherwise it will be invoked again
			//   by the resource unload and it will fail at that time
			domain.removeResourceSetListener(testListener);
		}
	}

	/**
	 * Tests that pre-commit listeners cannot maliciously (or accidentally)
	 * close the transaction that is committing.
	 */
	public void test_precommit_cannotClose() {
		startReading();
		
		final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
		assertNotNull(book);
		
		commit();
		
		TestListener testListener = new TestListener() {
			public Command transactionAboutToCommit(ResourceSetChangeEvent event) {
				try {
					// cannot commit the transaction while it is committing
					event.getTransaction().commit();
					fail("Should have thrown IllegalStateException"); //$NON-NLS-1$
				} catch (Exception e) {
					// success
					trace("Got expected exception: " + e.getLocalizedMessage()); //$NON-NLS-1$
				}
				
				try {
					// cannot commit the transaction while it is committing
					event.getTransaction().rollback();
					fail("Should have thrown IllegalStateException"); //$NON-NLS-1$
				} catch (Exception e) {
					// success
					trace("Got expected exception: " + e.getLocalizedMessage()); //$NON-NLS-1$
				}
				
				return null;
			}};
		
		try {
			domain.addResourceSetListener(testListener);
			
			String newTitle = "New Title"; //$NON-NLS-1$
			
			Command cmd = new SetCommand(
				domain, book, EXTLibraryPackage.eINSTANCE.getBook_Title(), newTitle);
			
			domain.getCommandStack().execute(cmd);
		} catch (Exception e) {
			fail(e);
		} finally {
			// must remove the listener, otherwise it will be invoked again
			//   by the resource unload and it will fail at that time
			domain.removeResourceSetListener(testListener);
		}
	}
	
	/**
	 * Tests that trigger commands are executed correctly, and that they can
	 * avoid feed-back.
	 */
	public void test_triggerCommands() {
		// one trigger sets default library names
		domain.addResourceSetListener(new LibraryDefaultNameTrigger());
		
		// another (distinct) trigger creates default books in new libraries
		domain.addResourceSetListener(new LibraryDefaultBookTrigger());
		
		startWriting();
		
		// add a new library.  Our triggers will set a default name and book
		Library newLibrary = EXTLibraryFactory.eINSTANCE.createLibrary();
		root.getBranches().add(newLibrary);
		
		assertNull(newLibrary.getName());
		assertTrue(newLibrary.getBranches().isEmpty());
		
		commit();
		
		startReading();
		
		assertEquals("New Library", newLibrary.getName()); //$NON-NLS-1$
		assertEquals(1, newLibrary.getBooks().size());
		assertEquals("New Book", ((Book) newLibrary.getBooks().get(0)).getTitle()); //$NON-NLS-1$
		
		commit();
	}
	
	/**
	 * Tests that a command resulting from a pre-commit (trigger) listener will,
	 * itself, trigger further changes.
	 */
	public void test_triggerCommands_cascading() {
		// add the trigger to create a default book in a new library
		domain.addResourceSetListener(new LibraryDefaultBookTrigger());
		
		// add another trigger that will set default publication dates for new items
		domain.addResourceSetListener(new ItemDefaultPublicationDateTrigger());
		startWriting();
		
		// add a new library.  Our triggers will set a default name and book
		Library newLibrary = EXTLibraryFactory.eINSTANCE.createLibrary();
		root.getBranches().add(newLibrary);
		
		assertNull(newLibrary.getName());
		assertTrue(newLibrary.getBranches().isEmpty());
		
		commit();
		
		startReading();
		
		// the book is created by the first trigger
		assertEquals(1, newLibrary.getBooks().size());
		Book book = (Book) newLibrary.getBooks().get(0);
		assertEquals("New Book", book.getTitle()); //$NON-NLS-1$
		
		// the publication date is created by the cascaded trigger
		assertNotNull(book.getPublicationDate());
		
		commit();
	}
	
	/**
	 * Tests that post-commit listeners get not only the notifications generated
	 * by the transaction, itself, but also by its trigger commands.
	 */
	public void test_postcommitIncludesTriggerChanges() {
		// add the trigger to create a default book in a new library
		domain.addResourceSetListener(new LibraryDefaultBookTrigger());
		
		// add another trigger that will set default publication dates for new items
		domain.addResourceSetListener(new ItemDefaultPublicationDateTrigger());
		startWriting();
		
		// for tracking the post-commit event
		TestListener testListener = new TestListener();
		domain.addResourceSetListener(testListener);
		
		// add a new library.  Our triggers will set a default name and book
		Library newLibrary = EXTLibraryFactory.eINSTANCE.createLibrary();
		root.getBranches().add(newLibrary);
		
		assertNull(newLibrary.getName());
		assertTrue(newLibrary.getBranches().isEmpty());
		
		commit();

		assertNotNull(testListener.postcommit);
		assertNotNull(testListener.postcommitNotifications);
		
		// only one notification actually is directly caused by the transaction.
		//   The others come from the (chained) triggers:  one adding a book to
		//   Library.stock; one for adding the book to Library.books; one for
		//   setting the book's Item.publicationDate
		assertEquals(4, testListener.postcommitNotifications.size());
	}

	/**
	 * Tests that simple changes are propagated to post-commit listeners.
	 */
	public void test_unbatchedNotifications() {
		try {
			// don't start any transaction, but cause notifications
			Resource newRes = domain.createResource("/tmp/test_unbatched.extlibrary"); //$NON-NLS-1$
			
			assertNotNull(listener.postcommit);
			
			List notifications = listener.postcommitNotifications;
			assertNotNull(notifications);
			
			// unbatched notifications are always singletons
			assertEquals(1, notifications.size());
			
			Notification notification = (Notification) notifications.get(0);
			assertSame(domain.getResourceSet(), notification.getNotifier());
			assertEquals(Notification.ADD, notification.getEventType());
			assertEquals(ResourceSet.RESOURCE_SET__RESOURCES, notification.getFeatureID(null));
			assertSame(newRes, notification.getNewValue());
		} catch (Exception e) {
			fail(e);
		}
	}

	/**
	 * Tests that events are correctly reused for unbatched notifications.
	 */
	public void test_unbatchedNotifications_reuseEvents_128445() {
		try {
			class Listener extends ResourceSetListenerImpl {
				ResourceSetChangeEvent lastEvent = null;
				List lastNotifications = null;
				int count = 0;
				
				public void resourceSetChanged(ResourceSetChangeEvent event) {
					count++;
					
					if (lastEvent == null) {
						lastEvent = event;
						lastNotifications = event.getNotifications();
					} else {
						assertSame(lastEvent, event);
						assertSame(lastNotifications, event.getNotifications());
						assertEquals(1, lastNotifications.size());
					}
				}
			}

			testResource.unload();
			
			Listener localListener = new Listener();
			
			domain.addResourceSetListener(localListener);
			
			// cause a bunch of unbatched events
			testResource.load(Collections.EMPTY_MAP);
			
			// check that there was actually an opportunity to reuse the event
			assertTrue(localListener.count > 1);
		} catch (Exception e) {
			fail(e);
		}
	}
	
	/**
	 * Tests that notifications resulting from reads performed by post-commit
	 * listeners are, themselves, batched and sent around again to the
	 * listeners.
	 */
	public void test_readNotifications_cascading() {
		final Resource[] newRes = new Resource[1];
		
		TestListener testListener = new TestListener() {
			public void resourceSetChanged(ResourceSetChangeEvent event) {
				// make sure that I only do this on the first time that I
				//    am invoked (from the new library notification)
				URI uri = URI.createFileURI("/tmp/test_readCascade.extlibrary"); //$NON-NLS-1$
				if (domain.getResourceSet().getResource(uri, false) == null) {
					// prepare the fixture listener to receive the read notification
					listener.reset();
					
					newRes[0] = domain.getResourceSet().createResource(uri);
				}
			}};
		
		domain.addResourceSetListener(testListener);
		
		try {
			startWriting();
			
			// add a new library.  Our listener will cause read notifications
			Library newLibrary = EXTLibraryFactory.eINSTANCE.createLibrary();
			root.getBranches().add(newLibrary);
			
			commit();
			
			// assert that the resource was created and stored
			assertNotNull(newRes[0]);
			
			// check that the read notifications cascaded (read in the listener
			//     caused more notifications to the listeners)
			assertNotNull(listener.postcommit);
			
			List notifications = listener.postcommitNotifications;
			assertNotNull(notifications);
			
			// unbatched notifications are always singletons
			assertEquals(1, notifications.size());
			
			Notification notification = (Notification) notifications.get(0);
			assertSame(domain.getResourceSet(), notification.getNotifier());
			assertEquals(Notification.ADD, notification.getEventType());
			assertEquals(ResourceSet.RESOURCE_SET__RESOURCES, notification.getFeatureID(null));
			assertSame(newRes[0], notification.getNewValue());
		} finally {
			domain.removeResourceSetListener(testListener);
		}
	}

	/**
	 * Tests that aggregated pre-commit listeners are notified only when the
	 * root-level read/write transaction commits, with all of the notifications
	 * from the transaction and any nested transactions.
	 */
	public void test_precommit_aggregated_121508() {
		try {
			class AggregatedListener extends TestListener {
				int count = 0;
				
				public Command transactionAboutToCommit(ResourceSetChangeEvent event)
					throws RollbackException {
					
					count++;
					
					return super.transactionAboutToCommit(event);
				}
				
				public void reset() {
					super.reset();
					
					count = 0;
				}
				
				public boolean isAggregatePrecommitListener() {
					return true;
				}
			}
			
			AggregatedListener localListener = new AggregatedListener();
			domain.addResourceSetListener(localListener);
			
			startReading();
			
			final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
			assertNotNull(book);
			
			commit();
			
			String newTitle1 = "New Title1"; //$NON-NLS-1$
			String newTitle2 = "New Title2"; //$NON-NLS-1$
			
			startWriting();
			
			book.setTitle(newTitle1);
			
			// nested
			startWriting();
			
			book.setTitle(newTitle2);
			
			commit();
			
			commit();
			
			assertNotNull(localListener.precommit);
			assertNotNull(localListener.precommit.getTransaction());
			assertEquals(1, localListener.count);
			
			List notifications = localListener.precommitNotifications;
			assertNotNull(notifications);
			assertEquals(2, notifications.size());
			
			// notifications came in the right order
			Notification notification = (Notification) notifications.get(0);
			assertSame(book, notification.getNotifier());
			assertSame(EXTLibraryPackage.eINSTANCE.getBook_Title(), notification.getFeature());
			assertSame(newTitle1, notification.getNewValue());
			
			notification = (Notification) notifications.get(1);
			assertSame(book, notification.getNotifier());
			assertSame(EXTLibraryPackage.eINSTANCE.getBook_Title(), notification.getFeature());
			assertSame(newTitle2, notification.getNewValue());
		} catch (Exception e) {
			fail(e);
		}
	}

	/**
	 * Tests that changes made by aggregated pre-commit listeners are fed back
	 * into those listeners again.
	 */
	public void test_precommit_aggregatedCascade_121508() {
		try {
			final String newTitle1 = "New Title1"; //$NON-NLS-1$
			final String newTitle2 = "New Title2"; //$NON-NLS-1$
			
			class AggregatedListener extends TestListener {
				int count = 0;
				
				public Command transactionAboutToCommit(ResourceSetChangeEvent event)
					throws RollbackException {
					
					count++;
					
					super.transactionAboutToCommit(event);
					
					if (count < 2) {
						List notifications = event.getNotifications();
						assertNotNull(notifications);
						assertEquals(1, notifications.size());
						
						Notification notification = (Notification) notifications.get(0);
						assertSame(EXTLibraryPackage.eINSTANCE.getBook_Title(), notification.getFeature());
						assertSame(newTitle1, notification.getNewValue());
						
						Book book = (Book) notification.getNotifier();
						
						return new SetCommand(
							domain, book,
							EXTLibraryPackage.eINSTANCE.getBook_Title(),
							newTitle2);
					}
					
					return null;
				}
				
				public void reset() {
					super.reset();
					
					count = 0;
				}
				
				public boolean isAggregatePrecommitListener() {
					return true;
				}
			}
			
			AggregatedListener localListener = new AggregatedListener();
			domain.addResourceSetListener(localListener);
			
			startReading();
			
			final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
			assertNotNull(book);
			
			commit();
			
			startWriting();
			
			book.setTitle(newTitle1);
			
			commit();
			
			assertNotNull(localListener.precommit);
			assertNotNull(localListener.precommit.getTransaction());
			assertEquals(2, localListener.count);
			
			List notifications = localListener.precommitNotifications;
			assertNotNull(notifications);
			assertEquals(1, notifications.size());
			
			Notification notification = (Notification) notifications.get(0);
			assertSame(book, notification.getNotifier());
			assertSame(EXTLibraryPackage.eINSTANCE.getBook_Title(), notification.getFeature());
			assertSame(newTitle2, notification.getNewValue());
		} catch (Exception e) {
			fail(e);
		}
	}
	
	/**
	 * Tests the propagation of resource change events to post-commit listeners
	 * even in the case of a transaction rolling back, where the resource change
	 * is a URI change.
	 */
	public void test_rollback_resourceChangePropagation_uri_145321() {
		class ResourceListener extends DemultiplexingListener {
			boolean wasCalled;
			
			protected void handleNotification(TransactionalEditingDomain domain, Notification notification) {
				wasCalled = true;
			}
		}
		
		Resource test1 = domain.getResourceSet().createResource(URI.createURI("http://foo1.xmi")); //$NON-NLS-1$
		Book book = EXTLibraryFactory.eINSTANCE.createBook();
		
		startWriting();
		
		// load the resource
		test1.getContents().add(book);
		
		commit();

		ResourceListener listener = new ResourceListener();
		domain.addResourceSetListener(listener);
		
		// now the meat of the test:  unload in a transaction
		startWriting();
		
		book.setTitle("foo"); //$NON-NLS-1$
		
		rollback();
		
		// contents change was rolled back
		assertNull(book.getTitle());
		
		// no notifications were sent out
		assertFalse(listener.wasCalled);
	}
	
	/**
	 * Tests that, when non-undoable changes such as resource loads do not occur
	 * during a transaction that was rolled back, listeners are not invoked.
	 */
	public void test_rollback_noEvents_145321() {
		class ResourceListener extends DemultiplexingListener {
			private final ResourceSet interestingResourceSet;
			private final Book interestingBook;
			
			boolean changed;
			
			ResourceListener(ResourceSet resourceSet, Book book) {
				interestingResourceSet = resourceSet;
				interestingBook = book;
			}
			
			protected void handleNotification(TransactionalEditingDomain domain, Notification notification) {
				Object notifier = notification.getNotifier();
				
				if (notifier == interestingResourceSet) {
					int featureID = notification.getFeatureID(ResourceSet.class);
					
					switch (featureID) {
					case ResourceSet.RESOURCE_SET__RESOURCES:
						changed = true;
						break;
					}
				} else if (notifier == interestingBook) {
					fail("Should not have received notification of contents change"); //$NON-NLS-1$
				}
			}
		}
		
		Resource test1 = domain.getResourceSet().createResource(URI.createURI("http://foo1.xmi")); //$NON-NLS-1$
		Book book = EXTLibraryFactory.eINSTANCE.createBook();
		
		ResourceListener listener = new ResourceListener(domain.getResourceSet(), book);
		domain.addResourceSetListener(listener);
		
		startWriting();
		
		test1.getContents().add(book);
		book.setTitle("foo"); //$NON-NLS-1$
		
		rollback();
		
		// contents change was rolled back
		assertNull(book.getTitle());
		
		// no resource set change occurred
		assertFalse(listener.changed);
	}
	
	/**
	 * Tests the propagation of resource change events to post-commit listeners
	 * even in the case of a transaction rolling back, where the resource change
	 * is a created change.
	 */
	public void test_rollback_resourceChangePropagation_created_145321() {
		class ResourceListener extends DemultiplexingListener {
			private final ResourceSet interestingResourceSet;
			private final Book interestingBook;
			
			boolean changed;
			
			ResourceListener(ResourceSet resourceSet, Book book) {
				interestingResourceSet = resourceSet;
				interestingBook = book;
			}
			
			protected void handleNotification(TransactionalEditingDomain domain, Notification notification) {
				Object notifier = notification.getNotifier();
				
				if (notifier == interestingResourceSet) {
					int featureID = notification.getFeatureID(ResourceSet.class);
					
					switch (featureID) {
					case ResourceSet.RESOURCE_SET__RESOURCES:
						changed = true;
						break;
					}
				} else if (notifier == interestingBook) {
					fail("Should not have received notification of contents change"); //$NON-NLS-1$
				}
			}
		}
		
		Resource test1 = domain.getResourceSet().createResource(URI.createURI("http://foo1.xmi")); //$NON-NLS-1$
		Book book = EXTLibraryFactory.eINSTANCE.createBook();
		
		ResourceListener listener = new ResourceListener(domain.getResourceSet(), book);
		domain.addResourceSetListener(listener);
		
		startWriting();
		
		test1.getContents().add(book);
		book.setTitle("foo"); //$NON-NLS-1$
		
		// create another resource
		URI newURI = URI.createURI("http://newfoo.xmi"); //$NON-NLS-1$
		domain.getResourceSet().createResource(newURI);
		
		rollback();
		
		// contents change was rolled back
		assertNull(book.getTitle());
		
		// resource set state change was not
		assertNotNull(domain.getResourceSet().getResource(newURI, false));
		assertTrue(listener.changed);
	}
	
	/**
	 * Tests the propagation of resource change events to post-commit listeners
	 * even in the case of a transaction rolling back, where the resource change
	 * is a loaded change.
	 */
	public void test_rollback_resourceChangePropagation_loaded_145321() {
		class ResourceListener extends DemultiplexingListener {
			private final Resource interestingResource;
			private final Book interestingBook;
			
			boolean changed;
			
			ResourceListener(Resource resource, Book book) {
				interestingResource = resource;
				interestingBook = book;
			}
			
			protected void handleNotification(TransactionalEditingDomain domain, Notification notification) {
				Object notifier = notification.getNotifier();
				
				if (notifier == interestingResource) {
					int featureID = notification.getFeatureID(Resource.class);
					
					switch (featureID) {
					case Resource.RESOURCE__IS_LOADED:
						changed = true;
						break;
					}
				} else if (notifier == interestingBook) {
					fail("Should not have received notification of contents change"); //$NON-NLS-1$
				}
			}
		}
		
		Resource test1 = domain.getResourceSet().createResource(URI.createURI("http://foo1.xmi")); //$NON-NLS-1$
		Book book = EXTLibraryFactory.eINSTANCE.createBook();
		
		ResourceListener listener = new ResourceListener(test1, book);
		domain.addResourceSetListener(listener);
		
		startWriting();
		
		// causes a load event
		test1.getContents().add(book);
		book.setTitle("foo"); //$NON-NLS-1$
		
		rollback();
		
		// contents change was rolled back
		assertNull(book.getTitle());
		
		// loaded state change was not
		assertTrue(test1.isLoaded());
		assertTrue(listener.changed);
	}
	
	/**
	 * Tests the propagation of resource change events to post-commit listeners
	 * even in the case of a transaction rolling back, where the resource change
	 * is an unloaded change.
	 */
	public void test_rollback_resourceChangePropagation_unloaded_145321() {
		class ResourceListener extends DemultiplexingListener {
			private final Resource interestingResource;
			private final Book interestingBook;
			
			boolean changed;
			
			ResourceListener(Resource resource, Book book) {
				interestingResource = resource;
				interestingBook = book;
			}
			
			protected void handleNotification(TransactionalEditingDomain domain, Notification notification) {
				Object notifier = notification.getNotifier();
				
				if (notifier == interestingResource) {
					int featureID = notification.getFeatureID(Resource.class);
					
					switch (featureID) {
					case Resource.RESOURCE__IS_LOADED:
						changed = true;
						break;
					}
				} else if (notifier == interestingBook) {
					fail("Should not have received notification of contents change"); //$NON-NLS-1$
				}
			}
		}
		
		Resource test1 = domain.getResourceSet().createResource(URI.createURI("http://foo1.xmi")); //$NON-NLS-1$
		Book book = EXTLibraryFactory.eINSTANCE.createBook();
		
		startWriting();
		
		// load the resource
		test1.getContents().add(book);
		
		commit();

		ResourceListener listener = new ResourceListener(test1, book);
		domain.addResourceSetListener(listener);
		
		// now the meat of the test:  unload in a transaction
		startWriting();
		
		book.setTitle("foo"); //$NON-NLS-1$
		test1.unload();
		
		rollback();
		
		// contents change was rolled back
		assertNull(book.getTitle());
		
		// loaded state change was not
//TODO:	Proxies are being added back into the resource!		
//		assertFalse(test1.isLoaded());
		assertTrue(listener.changed);
	}
    
    /**
     * Tests that the {@link RecordingCommand} can be used as a trigger command,
     * that in this case it is able correctly to capture its changes for
     * undo/redo.
     */
    public void test_recordingCommandsAsTriggers_bug157103() {
        // one trigger sets default library names
        domain.addResourceSetListener(new LibraryDefaultNameTrigger() {
            protected Command trigger(TransactionalEditingDomain domain, Notification notification) {
                Command result = null;
                
                final Library newLibrary = (Library) notification.getNewValue();
                if ((newLibrary.getName() == null) || (newLibrary.getName().length() == 0)) {
                    result = new RecordingCommand(domain) {
                        protected void doExecute() {
                            newLibrary.setName("New Library"); //$NON-NLS-1$
                        }};
                }
                
                return result;
            }});
        
        final Library[] newLibrary = new Library[1];
        
        // add a new library.  Our trigger will set a default name
        domain.getCommandStack().execute(new RecordingCommand(domain) {
            protected void doExecute() {
                newLibrary[0] = EXTLibraryFactory.eINSTANCE.createLibrary();
                root.getBranches().add(newLibrary[0]);
                
                assertNull(newLibrary[0].getName());
            }});
        
        startReading();
        
        assertEquals("New Library", newLibrary[0].getName()); //$NON-NLS-1$
        
        commit();
        
        domain.getCommandStack().undo();
        
        assertFalse(root.getBranches().contains(newLibrary[0]));
        assertNull(newLibrary[0].eResource());
        assertNull(newLibrary[0].getName());
        
        domain.getCommandStack().redo();
        
        assertTrue(root.getBranches().contains(newLibrary[0]));
        assertEquals("New Library", newLibrary[0].getName()); //$NON-NLS-1$
    }
	
	//
	// Fixture methods
	//
	
	protected void doSetUp()
		throws Exception {
		
		super.doSetUp();
		
		// in case the resource that we created tracks modification, we
		//    don't want this because it will add notifications that
		//    confuse our counts when we attach listeners to gather
		//    notifications
		testResource.setTrackingModification(false);
		
		listener = new TestListener();
		domain.addResourceSetListener(listener);
	}
	
	protected void doTearDown()
		throws Exception {
		
		domain.removeResourceSetListener(listener);
		listener = null;
		
		super.doTearDown();
	}
}
