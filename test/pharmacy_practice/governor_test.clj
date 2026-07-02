(ns pharmacy-practice.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [pharmacy-practice.store :as store]
            [pharmacy-practice.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-patient! st {:patient-id "patient-1"})
    st))

(deftest proceeds-on-verified-non-controlled-dispense
  (let [st (fresh-store)
        _ (store/record-verification! st {:verification-id "v1" :patient-id "patient-1"
                                             :drug "amoxicillin" :prescriber-verified? true})
        env (governor/env-for-store st)
        proposal {:kind :dispense :patient-id "patient-1" :drug "amoxicillin"
                   :quantity 20 :controlled-substance? false :safety-class :low
                   :effect :propose :confidence 0.9}]
    (is (= :proceed (:decision (governor/assess env proposal))))))

(deftest holds-on-unregistered-patient
  (let [st (fresh-store)
        env (governor/env-for-store st)
        proposal {:kind :dispense :patient-id "no-such-patient" :drug "amoxicillin"
                   :controlled-substance? false :safety-class :low :effect :propose :confidence 0.9}
        result (governor/assess env proposal)]
    (is (= :hold (:decision result)))
    (is (some #(= :no-patient (:rule %)) (:violations result)))))

(deftest holds-on-no-actuation-violation
  (let [st (fresh-store)
        env (governor/env-for-store st)
        proposal {:kind :verification :patient-id "patient-1" :drug "amoxicillin"
                   :safety-class :low :effect :direct-write :confidence 0.9}
        result (governor/assess env proposal)]
    (is (= :hold (:decision result)))
    (is (some #(= :no-actuation (:rule %)) (:violations result)))))

(deftest holds-on-dispense-without-verification
  (let [st (fresh-store)
        env (governor/env-for-store st)
        proposal {:kind :dispense :patient-id "patient-1" :drug "amoxicillin"
                   :controlled-substance? false :safety-class :low :effect :propose :confidence 0.9}
        result (governor/assess env proposal)]
    (is (= :hold (:decision result)))
    (is (some #(= :verification-required (:rule %)) (:violations result)))))

(deftest holds-on-controlled-substance-dispense-without-high-safety-class
  (let [st (fresh-store)
        _ (store/record-verification! st {:verification-id "v1" :patient-id "patient-1"
                                             :drug "oxycodone" :prescriber-verified? true})
        env (governor/env-for-store st)
        proposal {:kind :dispense :patient-id "patient-1" :drug "oxycodone"
                   :controlled-substance? true :safety-class :medium :effect :propose :confidence 0.9}
        result (governor/assess env proposal)]
    (is (= :hold (:decision result)))
    (is (some #(= :controlled-substance-safety (:rule %)) (:violations result)))))

(deftest human-approval-on-controlled-substance-dispense-with-high-safety-class
  (let [st (fresh-store)
        _ (store/record-verification! st {:verification-id "v1" :patient-id "patient-1"
                                             :drug "oxycodone" :prescriber-verified? true})
        env (governor/env-for-store st)
        proposal {:kind :dispense :patient-id "patient-1" :drug "oxycodone"
                   :controlled-substance? true :safety-class :high :effect :propose :confidence 0.9}]
    (is (= :human-approval (:decision (governor/assess env proposal))))))

(deftest human-approval-on-low-confidence
  (let [st (fresh-store)
        _ (store/record-verification! st {:verification-id "v1" :patient-id "patient-1"
                                             :drug "amoxicillin" :prescriber-verified? true})
        env (governor/env-for-store st)
        proposal {:kind :dispense :patient-id "patient-1" :drug "amoxicillin"
                   :controlled-substance? false :safety-class :none :effect :propose :confidence 0.2}
        result (governor/assess env proposal)]
    (is (= :human-approval (:decision result)))
    (is (= :low-confidence (:reason result)))))

(deftest store-records-append-only
  (let [st (fresh-store)]
    (store/record-verification! st {:verification-id "v1" :patient-id "patient-1"
                                      :drug "amoxicillin" :prescriber-verified? true})
    (store/record-dispense! st {:dispense-id "d1" :patient-id "patient-1"
                                  :drug "amoxicillin" :quantity 20 :controlled-substance? false})
    (is (= 1 (count (store/verifications-of st "patient-1"))))
    (is (= 1 (count (store/dispenses-of st "patient-1"))))))
