/**
 * ﻿Copyright (C) 2012
 * by 52 North Initiative for Geospatial Open Source Software GmbH
 *
 * Contact: Andreas Wytzisk
 * 52 North Initiative for Geospatial Open Source Software GmbH
 * Martin-Luther-King-Weg 24
 * 48155 Muenster, Germany
 * info@52north.org
 *
 * This program is free software; you can redistribute and/or modify it under
 * the terms of the GNU General Public License version 2 as published by the
 * Free Software Foundation.
 *
 * This program is distributed WITHOUT ANY WARRANTY; even without the implied
 * WARRANTY OF MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program (see gnu-gpl v2.txt). If not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA or
 * visit the Free Software Foundation web page, http://www.fsf.org.
 */
package org.n52.server.ses.service;

import static org.n52.shared.responses.SesClientResponse.types.RULE_NAME_EXISTS;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.n52.client.service.SesRuleService;
import org.n52.oxf.adapter.OperationResult;
import org.n52.server.ses.SesConfig;
import org.n52.server.ses.eml.BasicRule_1_Builder;
import org.n52.server.ses.eml.BasicRule_2_Builder;
import org.n52.server.ses.eml.BasicRule_3_Builder;
import org.n52.server.ses.eml.BasicRule_4_Builder;
import org.n52.server.ses.eml.BasicRule_5_Builder;
import org.n52.server.ses.eml.ComplexRule_Builder;
import org.n52.server.ses.eml.ComplexRule_BuilderV2;
import org.n52.server.ses.eml.Meta_Builder;
import org.n52.server.ses.feeder.FeederConfig;
import org.n52.server.ses.feeder.SosSesFeeder;
import org.n52.server.ses.hibernate.HibernateUtil;
import org.n52.server.ses.util.RulesUtil;
import org.n52.server.ses.util.SearchUtil;
import org.n52.server.ses.util.SesServerUtil;
import org.n52.shared.Constants;
import org.n52.shared.LogicalOperator;
import org.n52.shared.responses.SesClientResponse;
import org.n52.shared.responses.SesClientResponse.types;
import org.n52.shared.serializable.pojos.BasicRule;
import org.n52.shared.serializable.pojos.BasicRuleDTO;
import org.n52.shared.serializable.pojos.ComplexRule;
import org.n52.shared.serializable.pojos.ComplexRuleDTO;
import org.n52.shared.serializable.pojos.ComplexRuleData;
import org.n52.shared.serializable.pojos.Rule;
import org.n52.shared.serializable.pojos.Subscription;
import org.n52.shared.serializable.pojos.TimeseriesFeed;
import org.n52.shared.serializable.pojos.TimeseriesMetadata;
import org.n52.shared.serializable.pojos.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SesRulesServiceImpl implements SesRuleService {
    
    private static final Logger LOG = LoggerFactory.getLogger(SesRulesServiceImpl.class);
    
    @Override
    public SesClientResponse subscribe(String userID, String ruleName, String medium, String eml) throws Exception {
        try {
            LOG.debug("subscribe to rule: " + ruleName);
            LOG.debug("notification type:  " + medium);
            
            // get EML from DB
            BasicRule basicRule = HibernateUtil.getBasicRuleByName(ruleName);
            ComplexRule complexRule = HibernateUtil.getComplexRuleByName(ruleName);
            
            // get user from DBs
            User user = HibernateUtil.getUserBy(Integer.valueOf(userID));
            
            // subscribe basic rules from other user
            if (basicRule != null && basicRule.getOwnerID() != Integer.valueOf(userID)) {
                // copy rule and then continue
                SesClientResponse response = copy(userID, ruleName);
                if (response.getType().equals(SesClientResponse.types.RULE_NAME_EXISTS)) {
                    // rule name exists
                    return response;
                }
                // new rule name = originalRulaName_USERNAME
                String newRuleName = basicRule.getName() + "_" + user.getUserName();
                basicRule = HibernateUtil.getBasicRuleByName(newRuleName);
            }
            // subscribe complex rule from other user
            if (complexRule != null && complexRule.getOwnerID() != Integer.valueOf(userID)) {
                // copy rule and then continue
                SesClientResponse response = copy(userID, ruleName);
                if (response.equals(SesClientResponse.types.RULE_NAME_EXISTS)) {
                    // rule name exists
                    return response;
                }
                // new rule name = originalRulaName_USERNAME
                String newRuleName = complexRule.getName() + "_" + user.getUserName();
                complexRule = HibernateUtil.getComplexRuleByName(newRuleName);
            }
            
            // for all formats check whether such subscription already exists
            String content = "";
            String[] formats = eml.split("_");
            String[] media = medium.split("_");
            boolean subscriptionExists = false;
            boolean subscriptionsExists = false;
            
            for (int k = 0; k < media.length; k++) {
                for (int i = 0; i < formats.length; i++) {
                    subscriptionExists = false;
                    
                    if (basicRule != null && HibernateUtil.existsSubscription(basicRule.getId(), media[k], formats[i], Integer.valueOf(userID))) {
                        subscriptionExists = true;
                        subscriptionsExists = true;
                    } else if (complexRule != null && HibernateUtil.existsSubscription(complexRule.getId(), media[k], formats[i], Integer.valueOf(userID))) {
                        subscriptionExists = true;
                        subscriptionsExists = true;
                    }
                    // if subscription does not exists
                    if (!subscriptionExists) {
                        // create meta pattern
                        String meta = "";
                        content = "";
                        
                        // create meta pattern for basic rule
                        if (basicRule != null) {
                            content = basicRule.getEml();
                            
                            if (!basicRule.getType().equals("BR5")) {
                                if (formats[i].equals("Text")) {
                                    meta = Meta_Builder.createTextMeta(user, basicRule.getName(), media[k]);
                                } else {
                                    meta = Meta_Builder.createXMLMeta(user, basicRule.getName(), media[k], formats[i]);
                                }
                                // other rule types
                            } else {
                                if (formats[i].equals("Text")) {
                                    TimeseriesMetadata metadata = basicRule.getTimeseriesMetadata();
                                    meta = Meta_Builder.createTextFailureMeta(user, basicRule, media[k], metadata.getProcedure()); 
                                } else {
                                    meta = Meta_Builder.createXMLMeta(user, basicRule.getName(), media[k], formats[i]);
                                }
                            }
                            // create meta pattern for complex rule
                        } else if (complexRule != null) {
                            content = complexRule.getEml();
                            if (formats[i].equals("Text")) {
                                meta = Meta_Builder.createTextMeta(user, complexRule.getName(), media[k]);
                            } else {
                                meta = Meta_Builder.createXMLMeta(user, complexRule.getName(), media[k], formats[i]);
                            }
                        }

                        // add meta to EML
                        StringBuffer buffer = new StringBuffer(content);
                        int m = buffer.indexOf("<SimplePatterns>") + 16;
                        content = buffer.insert(m, meta).toString();

                        String museResource;
                        try {
                            // subscribe to SES
                            OperationResult opResult =
                                SesServerUtil.subscribe(SesConfig.serviceVersion, SesConfig.sesEndpoint, SesConfig.consumerReference, content);
                            museResource = SesServerUtil.getSubscriptionIDfromSES(opResult);
                            if ((museResource == null) || (museResource.equals(""))) {
                                throw new IllegalArgumentException("Illegal Muse resource");
                            }
                        } catch (Exception e) {
                            LOG.error("Error while subscribing to SES", e);
                            return new SesClientResponse(SesClientResponse.types.ERROR_SUBSCRIBE_SES);
                        }

                        // save subscription in DB
                        LOG.debug("save subscription to DB: " + museResource);
                        if (basicRule != null) {
                            HibernateUtil.saveSubscription(new Subscription(Integer.valueOf(userID), basicRule.getId(), museResource, media[k], formats[i]));
                            HibernateUtil.updateBasicRuleSubscribtion(basicRule.getName(), true);
                        } else if (complexRule != null) {
                            HibernateUtil.saveSubscription(new Subscription(Integer.valueOf(userID), complexRule.getId(), museResource, media[k], formats[i]));
                            HibernateUtil.updateComplexRuleSubscribtion(complexRule.getName(), true);
                        }
                        
                        // set sensor status to used
                        LOG.debug("set sensor to used");
                        ArrayList<String> timeseriesIds = SesServerUtil.getTimeseriesIdsFromEML(content);
                        
                        // check if sensor is allready in feeder DB. If yes --> no new request to feeder
                        try {
                            for (String timeseriesId : timeseriesIds) {
                                TimeseriesFeed timeseriesFeed = HibernateUtil.getTimeseriesFeedById(timeseriesId);
                                if (timeseriesFeed == null) {
                                    TimeseriesMetadata metadata = HibernateUtil.getTimeseriesMetadata(timeseriesId);
                                    LOG.debug("Create TimeseriesFeed: " + timeseriesId);
                                    SosSesFeeder.getInst().enableTimeseriesForFeeding(createTimeseriesFeed(metadata));
                                } else if (timeseriesFeed.getInUse() == 0) {
                                    LOG.debug("(Re-)enable TimeseriesFeed: " + timeseriesId);
                                    SosSesFeeder.getInst().enableTimeseriesForFeeding(timeseriesFeed);
                                }
                            }
                        } catch (Exception e) {
                            LOG.error("Error subscribing to feeder.", e);
                            return new SesClientResponse(SesClientResponse.types.ERROR_SUBSCRIBE_FEEDER);
                        }
                        
                        // increment sensor use count
                        try {
                            for (int j = 0; j < timeseriesIds.size(); j++) {
                                HibernateUtil.updateSensorCount(timeseriesIds.get(j), true);
                            }
                        } catch (Exception e) {
                            LOG.error("Could not update database", e);
                            throw new Exception("Failed set sensor to use!");
                        }
                    }
                }
            }

            if (subscriptionsExists) {
                return new SesClientResponse(SesClientResponse.types.SUBSCRIPTION_EXISTS);
            }
            return new SesClientResponse(SesClientResponse.types.OK);
        }
        catch (Exception e) {
            LOG.error("Exception occured on server side.", e);
            throw e; // last chance to log on server side
        }
    }
    
    private TimeseriesFeed createTimeseriesFeed(TimeseriesMetadata timeseriesMetadata) {
        TimeseriesFeed timeseriesFeed = new TimeseriesFeed(timeseriesMetadata);
        timeseriesFeed.setUpdateInterval(FeederConfig.getFeederConfig().getUpdateInterval());
        timeseriesFeed.setLastUpdate(null);
        timeseriesFeed.setUsedCounter(0);
        timeseriesFeed.setSesId(null);
        return timeseriesFeed;
    }

    @Override
    public SesClientResponse unSubscribe(String ruleName, String userID, String medium, String eml) throws Exception {
        try {
            LOG.debug("unsubscribe: " + ruleName);

            // get rule
            BasicRule basicRule = HibernateUtil.getBasicRuleByName(ruleName);
            ComplexRule complexRule = HibernateUtil.getComplexRuleByName(ruleName);
            
            // rule as EML
            String ruleAsEML = "";

            // get subscriptionID
            String museID = "";
            if (basicRule != null) {
                museID = HibernateUtil.getSubscriptionID(basicRule.getId(), medium, eml, Integer.valueOf(userID));
                ruleAsEML = basicRule.getEml();
            } else if (complexRule != null) {
                museID = HibernateUtil.getSubscriptionID(complexRule.getId(), medium, eml, Integer.valueOf(userID));
                ruleAsEML = complexRule.getEml();
            }

            try {
                // unsubscribe from SES
                LOG.debug("unsubscribe from SES: " + museID);
                SesServerUtil.unSubscribe(SesConfig.serviceVersion, SesConfig.sesEndpoint, museID);
            } catch (Exception e) {
                LOG.error("Failed to unsubscribe", e);
                return new SesClientResponse(SesClientResponse.types.ERROR_UNSUBSCRIBE_SES);
            }
            
            try {
                //TODO what happens if inUse is < 0??
                // remove unused sensor from feeder
                ArrayList<String> timeseriesFeedIds = SesServerUtil.getTimeseriesIdsFromEML(ruleAsEML);
                for (String timeseriesId : timeseriesFeedIds) {
                    
                    // decrement usedCount
                    HibernateUtil.updateSensorCount(timeseriesId, false);
                    
                    if (HibernateUtil.getTimeseriesFeedById(timeseriesId).getInUse() == 0) {
                        LOG.debug("remove sensor from used list");
                     // TODO change unsubscribe interface
//                        SosSesFeeder.getInst().removeUsedSensor(sensorID);
                    }
                }
                for (int i = 0; i < timeseriesFeedIds.size(); i++) {
                    
                }
            } catch (Exception e) {
                //TODO error handling??
                LOG.error("decrement sensor count or remove used sensor from feeder failed!", e);
            }

            try {
                // delete subscription from DB
                HibernateUtil.deleteSubscription(museID, userID);
                
                
                if (basicRule != null && !HibernateUtil.existsOtherSubscriptions(basicRule.getId())) {
                    HibernateUtil.updateBasicRuleSubscribtion(ruleName, false);
                } else if (complexRule != null && !HibernateUtil.existsOtherSubscriptions(complexRule.getId())) {
                    HibernateUtil.updateComplexRuleSubscribtion(ruleName, false);
                }
                
            } catch (Exception e) {
                throw new Exception("Failed delete subscription from DB!", e);
            }
            return new SesClientResponse(SesClientResponse.types.OK);
        }
        catch (Exception e) {
            LOG.error("Exception occured on server side.", e);
            throw e; // last chance to log on server side
        }
    }

    @Override
    public SesClientResponse createBasicRule(Rule rule, boolean edit, String oldRuleName) throws Exception {
        try {
            LOG.debug("createBasicRule: " + rule.getTitle());
            if (exists(rule) && !edit) {
                LOG.debug("Cannot create rule: Rule '{}' already exists!", rule.getTitle());
                return new SesClientResponse(RULE_NAME_EXISTS);
            } 

            TimeseriesMetadata metadata = rule.getTimeseriesMetadata();
            List<TimeseriesFeed> timeseriesFeeds = HibernateUtil.getActiveTimeseriesFeeds();
            for (TimeseriesFeed timeseriesFeed : timeseriesFeeds) {
                if (timeseriesFeeds.contains(metadata)) {
                    TimeseriesMetadata timeseriesMetadata = timeseriesFeed.getTimeseriesMetadata();
                    metadata.setProcedure(timeseriesMetadata.getProcedure());
                }
            }

            BasicRule basicRule = null;
            switch (rule.getRuleType()) {
            case SUM_OVER_TIME:
                basicRule = BasicRule_3_Builder.create_BR_3(rule);
                break;
            case TENDENCY_OVER_COUNT:
                basicRule = BasicRule_1_Builder.create_BR_1(rule);
                break;
            case TENDENCY_OVER_TIME:
                basicRule = BasicRule_2_Builder.create_BR_2(rule);
                break;
            case OVER_UNDERSHOOT:
                BasicRule_4_Builder ruleGenerator = new BasicRule_4_Builder();
                basicRule = ruleGenerator.create(rule);
                break;
            case SENSOR_LOSS:
                basicRule = BasicRule_5_Builder.create_BR_5(rule);
                break;
            }

            if (basicRule != null) {
                basicRule.setTimeseriesMetadata(rule.getTimeseriesMetadata());
                
                // user wants to edit the rule
                if (edit) {
                    // update Basic rule
                    LOG.debug("update basicRule in DB");
                    BasicRule oldRule = HibernateUtil.getBasicRuleByName(oldRuleName);
                    
                    // check if only description and/or publish status is changed ==> no resubscriptions are needed
                    if (RulesUtil.changesOnlyInDBBasic(oldRule, basicRule)) {
                        // update in DB only
                        // delete old rule
                        HibernateUtil.deleteRule(oldRuleName);
                        // save new
                        HibernateUtil.saveBasicRule(basicRule);
                        
                        return new SesClientResponse(types.EDIT_SIMPLE_RULE);
                    }
                    
                    // rule is subscribed
                    if (oldRule.isSubscribed()) {
                        List<Subscription> subscriptions = HibernateUtil.getSubscriptionsFromRuleID(oldRule.getId());
                        // delete old rule
                        HibernateUtil.deleteRule(oldRuleName);
                        // save new
                        HibernateUtil.saveBasicRule(basicRule);
                        
                        // iterate over all subscriptions of this rule
                        // unsubscribe old rule and subscribe the edited rule
                        for (int i = 0; i < subscriptions.size(); i++) {
                            Subscription subscription = subscriptions.get(i);
                            try {
                                // unsubscribe from SES
                                LOG.debug("unsubscribe from SES: " + subscription.getSubscriptionID());
                                SesServerUtil.unSubscribe(SesConfig.serviceVersion, SesConfig.sesEndpoint, subscription.getSubscriptionID());
                                subscribe(String.valueOf(rule.getUserID()), rule.getTitle(), subscription.getMedium(), subscription.getFormat());
                            } catch (Exception e) {
                                LOG.error("Could not unsubscribe from SES", e);
                            }
                            HibernateUtil.deleteSubscription(subscription.getSubscriptionID(), String.valueOf(subscription.getUserID()));
                        }
                    } else {
                        // delete old rule
                        HibernateUtil.deleteRule(oldRuleName);
                        // save new
                        HibernateUtil.saveBasicRule(basicRule);
                    }

                    return new SesClientResponse(types.EDIT_SIMPLE_RULE);
                }
                LOG.debug("save basicRule to DB");
                HibernateUtil.saveBasicRule(basicRule);
            }
            return new SesClientResponse(types.RULE_NAME_NOT_EXISTS, rule);
        }
        catch (Exception e) {
            LOG.error("Exception occured on server side.", e);
            throw e; // last chance to log on server side
        }
    }

    boolean exists(Rule rule) {
        return HibernateUtil.existsBasicRuleName(rule.getTitle()) || HibernateUtil.existsComplexRuleName(rule.getTitle());
    }

    @Override
    public SesClientResponse getAllOwnRules(String id, boolean edit) throws Exception {
        try {
            LOG.debug("getAllOwnRules of user: " + id);

            ArrayList<BasicRuleDTO> finalBasicList = new ArrayList<BasicRuleDTO>();
            ArrayList<ComplexRuleDTO> finalComplexList = new ArrayList<ComplexRuleDTO>();
            List<BasicRule> basicList;
            List<ComplexRule> complexList;

            // get rules from DB
            basicList = HibernateUtil.getAllBasicRulesBy(id);
            complexList = HibernateUtil.getAllComplexRulesBy(id);

            // basic rules
            for (int i = 0; i < basicList.size(); i++) {
                BasicRule rule = basicList.get(i);

                // check if user subscribed this rule
                if (HibernateUtil.isSubscribed(id, rule.getId())) {
                    rule.setSubscribed(true);
                } else {
                    rule.setSubscribed(false);
                }

                finalBasicList.add(SesUserServiceImpl.createBasicRuleDTO(rule));
            }

            // complex rules
            for (int i = 0; i < complexList.size(); i++) {
                ComplexRule rule = complexList.get(i);

                // check if user subscribed this rule
                if (HibernateUtil.isSubscribed(id, rule.getId())) {
                    rule.setSubscribed(true);
                } else {
                    rule.setSubscribed(false);
                }

                finalComplexList.add(SesUserServiceImpl.createComplexRuleDTO(rule));
            }

            if (edit) {
                return new SesClientResponse(SesClientResponse.types.EDIT_OWN_RULES, finalBasicList, finalComplexList);
            }

            return new SesClientResponse(SesClientResponse.types.OWN_RULES, finalBasicList, finalComplexList);
        }
        catch (Exception e) {
            LOG.error("Exception occured on server side.", e);
            throw e; // last chance to log on server side
        }
    }

    @Override
    public SesClientResponse getAllOtherRules(String id, boolean edit) throws Exception {
        try {
            LOG.debug("get all rules except user: " + id);
            ArrayList<BasicRuleDTO> finalBasicList = new ArrayList<BasicRuleDTO>();
            ArrayList<ComplexRuleDTO> finalComplexList = new ArrayList<ComplexRuleDTO>();
            List<BasicRule> basicList;
            List<ComplexRule> complexList;
            
            // get rules from DB
            basicList = HibernateUtil.getAllOtherBasicRules(id);
            complexList = HibernateUtil.getAllOtherComplexRules(id);
            
            // basic rules
            for (int i = 0; i < basicList.size(); i++) {
                BasicRule rule = basicList.get(i);

                // show only published rules
                if (rule.isPublished()) {

                    // check if user subscribed this rule
                    if (HibernateUtil.isSubscribed(id, rule.getId())) {
                        rule.setSubscribed(true);
                    } else {
                        rule.setSubscribed(false);
                    }
                    finalBasicList.add(SesUserServiceImpl.createBasicRuleDTO(rule));
                }
            }
            
            // complex rules
            for (int i = 0; i < complexList.size(); i++) {
                ComplexRule rule = complexList.get(i);

                // show only published rules
                if (rule.isPublished()) {

                    // check if user subscribed this rule
                    if (HibernateUtil.isSubscribed(id, rule.getId())) {
                        rule.setSubscribed(true);
                    } else {
                        rule.setSubscribed(false);
                    }
                    finalComplexList.add(SesUserServiceImpl.createComplexRuleDTO(rule));
                }
            }
            
            if (edit) {
                return new SesClientResponse(SesClientResponse.types.EDIT_OTHER_RULES, finalBasicList, finalComplexList);
            }
            
            return new SesClientResponse(SesClientResponse.types.OTHER_RULES, finalBasicList, finalComplexList);
        }
        catch (Exception e) {
            LOG.error("Exception occured on server side.", e);
            throw e; // last chance to log on server side
        }
    }

    @Override
    public SesClientResponse publishRule(String ruleName, boolean value, String role) throws Exception {
        try {
            LOG.debug("publish rule: " + ruleName + ": " + value);
            if (HibernateUtil.publishRule(ruleName, value)) {
                if (role.equals("ADMIN")) {
                    return new SesClientResponse(SesClientResponse.types.PUBLISH_RULE_ADMIN);
                }
                return new SesClientResponse(SesClientResponse.types.PUBLISH_RULE_USER);
            } else {
                LOG.error("Error occured while publish rule");
                throw new Exception("Failed publish rule: " + ruleName);
            }
        }
        catch (Exception e) {
            LOG.error("Exception occured on server side.", e);
            throw e; // last chance to log on server side
        }

    }

    @Override
    public SesClientResponse getAllRules() throws Exception {
        try {
            LOG.debug("get all rules");
            
            BasicRuleDTO basicDTO;
            ComplexRuleDTO complexDTO;
            
            ArrayList<BasicRuleDTO> finalBasicList = new ArrayList<BasicRuleDTO>();
            ArrayList<ComplexRuleDTO> finalComplexList = new ArrayList<ComplexRuleDTO>();
            
            List<BasicRule> basicList = HibernateUtil.getAllBasicRules();
            List<ComplexRule> complexList = HibernateUtil.getAllComplexRules();
            
            for (int i = 0; i < basicList.size(); i++) {
                basicDTO = SesUserServiceImpl.createBasicRuleDTO(basicList.get(i));
                basicDTO.setOwnerName(HibernateUtil.getUserBy(basicDTO.getOwnerID()).getUserName());
                finalBasicList.add(basicDTO);
            }
            
            for (int i = 0; i < complexList.size(); i++) {
                complexDTO = SesUserServiceImpl.createComplexRuleDTO(complexList.get(i));
                complexDTO.setOwnerName(HibernateUtil.getUserBy(complexDTO.getOwnerID()).getUserName());
                finalComplexList.add(complexDTO);
            }
            
            return new SesClientResponse(SesClientResponse.types.All_RULES, finalBasicList, finalComplexList);
        }
        catch (Exception e) {
            LOG.error("Exception occured on server side.", e);
            throw e; // last chance to log on server side
        }
    }

    @Override
    public SesClientResponse deleteRule(String ruleName) throws Exception {
        try {
            LOG.debug("delete rule: " + ruleName);
            
            // get rule
            BasicRule basicRule = HibernateUtil.getBasicRuleByName(ruleName);
            ComplexRule complexRule = HibernateUtil.getComplexRuleByName(ruleName);
            
            if (basicRule != null) {
                if (HibernateUtil.ruleIsSubscribed(basicRule.getId())) {
                    return new SesClientResponse(SesClientResponse.types.DELETE_RULE_SUBSCRIBED);
                }
            } else if (complexRule != null) {
                if (HibernateUtil.ruleIsSubscribed(complexRule.getId())) {
                    return new SesClientResponse(SesClientResponse.types.DELETE_RULE_SUBSCRIBED);
                }
            }
            
            if (HibernateUtil.deleteRule(ruleName)) {
                return new SesClientResponse(SesClientResponse.types.DELETE_RULE_OK);
            } else {
                LOG.error("Error occured while deleting a rule");
                throw new Exception("Delete rule failed!");
            }
        }
        catch (Exception e) {
            LOG.error("Exception occured on server side.", e);
            throw e; // last chance to log on server side
        }
    }

    @Override
    public SesClientResponse getRuleForEditing(String ruleName) throws Exception {
        try {
            BasicRule basicRule = HibernateUtil.getBasicRuleByName(ruleName);
            ComplexRule complexRule = HibernateUtil.getComplexRuleByName(ruleName);
            Rule rule = null;
            
            if (basicRule != null) {
                // check the ruletype
                if (basicRule.getType().equals("BR1")) {
                    rule = BasicRule_1_Builder.createRuleBy(basicRule);
                } else if (basicRule.getType().equals("BR2")) {
                    rule = BasicRule_2_Builder.getRuleByEML(basicRule);
                } else if (basicRule.getType().equals("BR3")) {
                    rule = BasicRule_3_Builder.getRuleByEml(basicRule);
                } else if (basicRule.getType().equals("BR4")) {
                    rule = new BasicRule_4_Builder().getRuleByEML(basicRule);
                } else if (basicRule.getType().equals("BR5")) {
                    rule = BasicRule_5_Builder.getRuleByEML(basicRule);
                }
                rule.setTitle(basicRule.getName());
                rule.setDescription(basicRule.getDescription());
                rule.setPublish(basicRule.isPublished());
                return new SesClientResponse(SesClientResponse.types.EDIT_SIMPLE_RULE, rule);
            }
            if (complexRule != null) {
                rule = new Rule();
                rule.setTitle(complexRule.getName());
                rule.setDescription(complexRule.getDescription());
                rule.setPublish(complexRule.isPublished());
                
                // tree representation of a complex rule
                String tree = complexRule.getTree();
                ArrayList<String> treeList = new ArrayList<String>();
                String[] elements = tree.split("_T_");
                for (int i = 0; i < elements.length; i++) {
                    String content = elements[i];
                    
                    // check whether the rule names still exist 
                    if ((!content.equals(LogicalOperator.AND.toString()) && !content.equals(LogicalOperator.OR.toString()) && !content.equals(LogicalOperator.AND_NOT.toString()))) {
                        if (!HibernateUtil.existsBasicRuleName(content) && !HibernateUtil.existsComplexRuleName(content)) {
                            content = Constants.SES_OP_SEPARATOR + content;
                        }
                    }
                    treeList.add(content);
                }
                
                SesClientResponse response = new SesClientResponse(SesClientResponse.types.EDIT_COMPLEX_RULE, treeList);
                response.setRule(rule);
                
                return response;
            }
            return null;
        }
        catch (Exception e) {
            LOG.error("Exception occured on server side.", e);
            throw e; // last chance to log on server side
        }
    }

    @Override
    public SesClientResponse getAllPublishedRules(String userID, int operator) throws Exception {
        try {
            LOG.debug("get all published rules");
            ArrayList<String> finalList = new ArrayList<String>();
            
            List<BasicRule> basicRuleList = new ArrayList<BasicRule>();
            List<ComplexRule> complexRuleList = new ArrayList<ComplexRule>();
            
            // 1 = own
            // 2 = other
            // 3 = both
            if (operator == 1) {
                basicRuleList.addAll(HibernateUtil.getAllBasicRulesBy(userID));
                complexRuleList.addAll(HibernateUtil.getAllComplexRulesBy(userID));
            } else if (operator == 2) {
                basicRuleList.addAll(HibernateUtil.getAllOtherPublishedBasicRules(userID));
                complexRuleList.addAll(HibernateUtil.getAllOtherPublishedComplexRules(userID));
            } else if (operator == 3) {
                basicRuleList.addAll(HibernateUtil.getAllBasicRulesBy(userID));
                complexRuleList.addAll(HibernateUtil.getAllComplexRulesBy(userID));
                basicRuleList.addAll(HibernateUtil.getAllPublishedBR());
                complexRuleList.addAll(HibernateUtil.getAllPublishedCR());
            }
            // HashSet is used to avoid duplicates
            HashSet<String> h = new HashSet<String>();
            
            for (int i = 0; i < basicRuleList.size(); i++) {
                h.add(basicRuleList.get(i).getName());
            }
            
            for (int i = 0; i < complexRuleList.size(); i++) {
                h.add(complexRuleList.get(i).getName());
            }
            
            finalList.addAll(h);
            
            return new SesClientResponse(SesClientResponse.types.ALL_PUBLISHED_RULES, finalList);
        }
        catch (Exception e) {
            LOG.error("Exception occured on server side.", e);
            throw e; // last chance to log on server side
        }
    }

    @Override
    public SesClientResponse ruleNameExists(String ruleName) throws Exception {
        try {
            LOG.debug("check whether rule name '{}' exists.", ruleName);
            if (HibernateUtil.existsBasicRuleName(ruleName) || HibernateUtil.existsComplexRuleName(ruleName)) {
                return new SesClientResponse(SesClientResponse.types.RULE_NAME_EXISTS);
            }
            return new SesClientResponse(SesClientResponse.types.RULE_NAME_NOT_EXISTS);
        }
        catch (Exception e) {
            LOG.error("Exception occured on server side.", e);
            throw e; // last chance to log on server side
        }
    }

    @Override
    public SesClientResponse createComplexRule(ComplexRuleData rule, boolean edit, String oldRuleName) throws Exception {
        try {
            LOG.debug("create complex rule: " + rule.getTitle());
            
            // rule name exists
            if ((HibernateUtil.existsComplexRuleName(rule.getTitle()) && !edit) || HibernateUtil.existsBasicRuleName(rule.getTitle())) {
                return new SesClientResponse(SesClientResponse.types.RULE_NAME_EXISTS);
            }
            
            ArrayList<String> ruleNames = rule.getRuleNames();
            ArrayList<Object> rules = new ArrayList<Object>();
            
            ComplexRule finalComplexRule = null;
            BasicRule basicRule = null;
            ComplexRule complexRule = null;

            // combine only 2 rules
            if (rule.getRuleNames() != null) {
                // operator
                String operator = rule.getRuleNames().get(0);

                // get all used rules
                for (int i = 1; i < ruleNames.size(); i++) {
                    basicRule = HibernateUtil.getBasicRuleByName(ruleNames.get(i));
                    complexRule = HibernateUtil.getComplexRuleByName(ruleNames.get(i));
                    
                    if (basicRule != null) {
                        rules.add(basicRule);
                        if (basicRule.getOwnerID() != rule.getUserID()) {
                            copy(String.valueOf(rule.getUserID()), basicRule.getName());
                        }
                        
                    }
                    if (complexRule != null) {
                        rules.add(complexRule);
                        if (complexRule.getOwnerID() != rule.getUserID()) {
                            copy(String.valueOf(rule.getUserID()), complexRule.getName());
                        }
                    }
                }
                finalComplexRule = ComplexRule_Builder.combine2Rules(operator, rules, rule);
            } else {
                // combine 3 or more rules
                finalComplexRule = ComplexRule_BuilderV2.combineRules(rule, rule.getTreeContent());
            }

            if (finalComplexRule != null) {
                
                // set sensors
                String sensors = "";
                ArrayList<String> timeseriesFeedIdsList = SesServerUtil.getTimeseriesIdsFromEML(finalComplexRule.getEml());
                
                for (int i = 0; i < timeseriesFeedIdsList.size(); i++) {
                    sensors = sensors + timeseriesFeedIdsList.get(i);
                    sensors = sensors + "&";
                }
                finalComplexRule.setSensor(sensors);
                
                // set Phenomenona
                String phenomena = "";
                ArrayList<String> phenomenaList = SesServerUtil.getPhenomenaFromEML(finalComplexRule.getEml());
               
                for (int i = 0; i < phenomenaList.size(); i++) {
                    phenomena = phenomena + phenomenaList.get(i);
                    phenomena = phenomena + "&";
                }
                finalComplexRule.setPhenomenon(phenomena);
                
                
                if (edit) {
                    // update Complex rule
                    LOG.debug("update complex rule in DB");
                    ComplexRule oldRule = HibernateUtil.getComplexRuleByName(oldRuleName);
                    
                    // check if only description and/or publish status is changed ==> no resubscriptions are needed
                    if (RulesUtil.changesOnlyInDBComplex(oldRule, finalComplexRule)) {
                        // update in DB only
                        // delete old rule
                        HibernateUtil.deleteRule(oldRuleName);
                        // save new
                        HibernateUtil.addComplexRule(finalComplexRule);
                        
                        return new SesClientResponse(types.EDIT_COMPLEX_RULE);
                    }
                    
                    
                    if (oldRule.isSubscribed()) {
                        List<Subscription> subscriptions = HibernateUtil.getSubscriptionsFromRuleID(oldRule.getId());
                        // delete old rule
                        HibernateUtil.deleteRule(oldRuleName);
                        // save new
                        HibernateUtil.addComplexRule(finalComplexRule);
                        
                        // resubscribe
                        for (int i = 0; i < subscriptions.size(); i++) {
                            Subscription subscription = subscriptions.get(i);
                            try {
                                // unsubscribe from SES
                                LOG.debug("unsubscribe from SES: " + subscription.getSubscriptionID());
                                SesServerUtil.unSubscribe(SesConfig.serviceVersion, SesConfig.sesEndpoint, subscription.getSubscriptionID());
                                subscribe(String.valueOf(rule.getUserID()), rule.getTitle(), subscription.getMedium(), subscription.getFormat());
                            } catch (Exception e) {
                                LOG.error("Error occured while unsubscribing a rule from SES: " + e.getMessage(), e);
                            }
                            HibernateUtil.deleteSubscription(subscription.getSubscriptionID(), String.valueOf(subscription.getUserID()));
                        }
                    } else {
                        // delete old rule
                        HibernateUtil.deleteRule(oldRuleName);
                        // save new
                        HibernateUtil.addComplexRule(finalComplexRule);
                    }
                    return new SesClientResponse(types.EDIT_COMPLEX_RULE);
                }
                HibernateUtil.addComplexRule(finalComplexRule);
            }
            
            return new SesClientResponse(SesClientResponse.types.OK);
        }
        catch (Exception e) {
            LOG.error("Exception occured on server side.", e);
            throw e; // last chance to log on server side
        }
    }

    @Override
    public SesClientResponse getUserSubscriptions(String userID) throws Exception {
        try {
            LOG.debug("get all subscriptions of user: " + userID);
            List<Subscription> subscriptions = HibernateUtil.getUserSubscriptions(userID);
            ArrayList<BasicRuleDTO> basicList = new ArrayList<BasicRuleDTO>();
            ArrayList<ComplexRuleDTO> complexList = new ArrayList<ComplexRuleDTO>();

            String medium = "";
            String format = "";
            
            BasicRule basicRule;
            ComplexRule complexRule;
            for (int i = 0; i < subscriptions.size(); i++) {
                Subscription subscription = subscriptions.get(i);
                medium = subscription.getMedium();
                format = subscription.getFormat();

                basicRule = HibernateUtil.getBasicRuleByID(subscription.getRuleID());
                
                if (basicRule != null) {
                    basicRule.setMedium(subscription.getMedium());
                    basicRule.setFormat(subscription.getFormat());
                    basicList.add(SesUserServiceImpl.createBasicRuleDTO(basicRule));
                }
                complexRule = HibernateUtil.getComplexRuleByID(subscription.getRuleID());
                if (complexRule != null) {
                    complexRule.setMedium(subscription.getMedium());
                    complexRule.setFormat(subscription.getFormat());
                    complexList.add(SesUserServiceImpl.createComplexRuleDTO(complexRule));
                }
            }
            return new SesClientResponse(SesClientResponse.types.USER_SUBSCRIPTIONS, basicList, complexList);
        }
        catch (Exception e) {
            LOG.error("Exception occured on server side.", e);
            throw e; // last chance to log on server side
        }
    }

    @Override
    public SesClientResponse search(String text, int criterion, String userID) throws Exception {
        try {
            LOG.debug("search");
            return SearchUtil.search(text, criterion, userID);
        }
        catch (Exception e) {
            LOG.error("Exception occured on server side.", e);
            throw e; // last chance to log on server side
        }
    }

    @Override
    public SesClientResponse copy(String userID, String ruleName) throws Exception {
        try {
            LOG.debug("copy rule to own rules" + ruleName);

            // get the selected rule
            BasicRule basicRule = HibernateUtil.getBasicRuleByName(ruleName);
            ComplexRule complexRule = HibernateUtil.getComplexRuleByName(ruleName);
            
            User user = HibernateUtil.getUserBy(Integer.valueOf(userID));
            
            String newRuleName = "";
            
            if (basicRule != null) {
                newRuleName = basicRule.getName() + "_" + user.getUserName();
                
                // check if allready exists
                if (HibernateUtil.existsBasicRuleName(newRuleName)) {
                    return new SesClientResponse(SesClientResponse.types.RULE_NAME_EXISTS);
                }
                
                basicRule.setName(newRuleName);
                basicRule.setOwnerID(Integer.valueOf(userID));
                HibernateUtil.saveCopiedBasicRule(basicRule);
            }
            
            if (complexRule != null) {
                newRuleName = complexRule.getName() + "_" + user.getUserName();
                
                // check if allready exists
                if (HibernateUtil.existsComplexRuleName(newRuleName)) {
                    return new SesClientResponse(SesClientResponse.types.RULE_NAME_EXISTS);
                }
                
                complexRule.setName(newRuleName);
                complexRule.setOwnerID(Integer.valueOf(userID));
                HibernateUtil.saveCopiedComplexRule(complexRule);
            }
            return new SesClientResponse(SesClientResponse.types.OK);
        }
        catch (Exception e) {
            LOG.error("Exception occured on server side.", e);
            throw e; // last chance to log on server side
        }
    }

}
