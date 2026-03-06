package com.server.repositories;

import com.server.entities.Message;
import com.server.entities.User;
import com.server.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.List;

public class MessageRepository {

    public Message save(Message message) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            session.persist(message);
            transaction.commit();
            return message;
        } catch (Exception e) {
            if (transaction != null && transaction.getStatus().canRollback()) {
                try {
                    transaction.rollback();
                } catch (Exception rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
            }
            e.printStackTrace();
            return null;
        }
    }

    public List<Message> findConversation(User u1, User u2) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from Message where (sender = :u1 and receiver = :u2) or (sender = :u2 and receiver = :u1) order by dateEnvoi asc",
                    Message.class)
                    .setParameter("u1", u1)
                    .setParameter("u2", u2)
                    .list();
        }
    }

    public List<Message> findOfflineMessages(User receiver) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session
                    .createQuery("from Message where receiver = :receiver and status = 'ENVOYE' order by dateEnvoi asc",
                            Message.class)
                    .setParameter("receiver", receiver)
                    .list();
        }
    }

    public List<Message> findRecentHistory(User user) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // Récupère les 50 derniers messages privés et globaux auxquels l'utilisateur a
            // accès
            return session.createQuery(
                    "from Message where receiver = :user or sender = :user or receiver is null order by dateEnvoi asc",
                    Message.class)
                    .setParameter("user", user)
                    .setMaxResults(100)
                    .list();
        }
    }

    public void markAsDelivered(List<Message> messages) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            for (Message m : messages) {
                m.setStatus("RECU");
                session.merge(m);
            }
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null)
                transaction.rollback();
            e.printStackTrace();
        }
    }
}
