"use strict";
const {onCall,HttpsError,onRequest}=require("firebase-functions/v2/https");
const {onDocumentWritten,onDocumentCreated}=require("firebase-functions/v2/firestore");
const {defineSecret,defineString}=require("firebase-functions/params");
const {initializeApp}=require("firebase-admin/app");
const {getFirestore,FieldValue}=require("firebase-admin/firestore");
const {getMessaging}=require("firebase-admin/messaging");
const crypto=require("node:crypto");
const {BONUS,PACK,PRICE,MONTHLY_LIMIT,validateMinutes,transition}=require("./policy");
initializeApp();const db=getFirestore();const region="asia-south1";
const domains=defineString("CAMPUS_DOMAINS",{default:"SET_YOUR_APPROVED_DOMAIN"});
const keyId=defineSecret("RAZORPAY_KEY_ID"), keySecret=defineSecret("RAZORPAY_KEY_SECRET"), webhookSecret=defineSecret("RAZORPAY_WEBHOOK_SECRET");
function fail(message,code="failed-precondition"){throw new HttpsError(code,message);}
function user(r){
 const a=r.auth;if(!a)fail("Sign in first.","unauthenticated");
 const allowed=domains.value().toLowerCase().split(",").map(x=>x.trim());
 const domain=String(a.token.email||"").toLowerCase().split("@")[1];
 if(!a.token.email_verified || !allowed.includes(domain))fail("A verified, approved college email is required.","permission-denied");
 return a.uid;
}
function str(v,max){if(typeof v!=="string" || !v.trim() || v.length>max)fail("Invalid or missing text.","invalid-argument");return v.trim();}
function id(v){const s=str(v,128);if(!/^[A-Za-z0-9_-]+$/.test(s))fail("Invalid ID.","invalid-argument");return s;}
function wrap(fn,options={}){return onCall({region,...options},async r=>{try{return await fn(r);}catch(e){if(e instanceof HttpsError)throw e; console.error(e);throw new HttpsError("failed-precondition",e.message||"Request failed.");}});}
const wallet=uid=>db.doc(`wallets/${uid}`);
function ledger(tx,uid,key,label,amount){tx.set(wallet(uid).collection("ledger").doc(key),{label,amount,createdAt:Date.now()});}
exports.bootstrap=wrap(async r=>{
 const uid=user(r);await db.runTransaction(async tx=>{
  const w=await tx.get(wallet(uid));if(w.exists)return;
  tx.set(wallet(uid),{balance:BONUS});
  tx.set(db.doc(`profiles/${uid}`),{name:String(r.auth.token.email).split("@")[0],skills:"",availability:"",bio:"",photo:"",ratingSum:0,ratingCount:0});
  ledger(tx,uid,"joining-bonus","Joining bonus",BONUS);
 });return {ok:true};
});
exports.profile=wrap(async r=>{
 const uid=user(r),d=r.data||{},patch={};
 for(const [k,max] of Object.entries({name:60,skills:200,availability:200,bio:500,photo:2000})) if(k in d){
  if(typeof d[k]!=="string" || d[k].length>max)fail("Invalid profile field.","invalid-argument");patch[k]=d[k].trim();
 }
 if("name" in patch && !patch.name)fail("Name cannot be empty.");
 if(patch.photo && !patch.photo.startsWith("https://firebasestorage.googleapis.com/"))fail("Upload a photo to Firebase Storage first.");
 await db.doc(`profiles/${uid}`).update(patch);return {ok:true};
});
exports.book=wrap(async r=>{
 const uid=user(r),d=r.data||{},mentor=id(d.mentor),minutes=validateMinutes(d.minutes);
 if(uid===mentor)fail("Choose another student.");
 const scheduledAt=d.scheduledAt;if(!Number.isSafeInteger(scheduledAt)||scheduledAt<Date.now()+60000||scheduledAt>Date.now()+90*86400000)fail("Choose a time between one minute and 90 days from now.");
 const topic=str(d.topic,120),location=str(d.location,120),ref=db.collection("sessions").doc();
 await db.runTransaction(async tx=>{
  const [w,p,m]=await Promise.all([tx.get(wallet(uid)),tx.get(db.doc(`profiles/${mentor}`)),tx.get(wallet(mentor))]);
  if(!p.exists||!m.exists)fail("Mentor does not exist.");if(!w.exists||w.data().balance<minutes)fail("Not enough credits.");
  tx.update(wallet(uid),{balance:w.data().balance-minutes});
  tx.create(ref,{learner:uid,mentor,members:[uid,mentor],topic,location,minutes,scheduledAt,status:"requested",confirmations:[],rated:[],updatedBy:uid,createdAt:Date.now()});
  ledger(tx,uid,ref.id+"-reserve","Reserved: "+topic,-minutes);
 });return {id:ref.id};
});
exports.session=wrap(async r=>{
 const uid=user(r),ref=db.doc(`sessions/${id(r.data.id)}`);
 await db.runTransaction(async tx=>{
  const snap=await tx.get(ref);if(!snap.exists)fail("Session not found.");
  const s=snap.data(),next=transition(s,uid,r.data.action);
  if(next.status===s.status && next.confirmations.length===s.confirmations.length)return;
  let target=null,amount=0,label="";
  if(next.status==="cancelled"){target=s.learner;amount=s.minutes;label="Refund: "+s.topic;}
  if(next.status==="completed"){target=s.mentor;amount=s.minutes;label="Earned: "+s.topic;}
  const w=target?await tx.get(wallet(target)):null;
  if(target && !w.exists)fail("Wallet missing. Contact administrator.");
  tx.update(ref,{status:next.status,confirmations:next.confirmations,updatedBy:uid});
  if(target){tx.update(wallet(target),{balance:w.data().balance+amount});ledger(tx,target,ref.id+"-"+next.status,label,amount);}
 });return {ok:true};
});
exports.rate=wrap(async r=>{
 const uid=user(r),ref=db.doc(`sessions/${id(r.data.id)}`),stars=r.data.stars;
 if(!Number.isInteger(stars)||stars<1||stars>5)fail("Choose 1 to 5 stars.");
 await db.runTransaction(async tx=>{
  const snap=await tx.get(ref);if(!snap.exists)fail("Session not found.");const s=snap.data();
  if(!s.members.includes(uid)||s.status!=="completed"||s.rated.includes(uid))fail("Rating not allowed.");
  const peer=uid===s.learner?s.mentor:s.learner,pr=db.doc(`profiles/${peer}`),p=await tx.get(pr);
  tx.update(pr,{ratingSum:(p.data().ratingSum||0)+stars,ratingCount:(p.data().ratingCount||0)+1});
  tx.update(ref,{rated:[...s.rated,uid]});tx.create(ref.collection("ratings").doc(uid),{stars,peer,createdAt:Date.now()});
 });return {ok:true};
});
exports.notice=wrap(async r=>{const uid=user(r);await db.collection("notices").add({author:uid,title:str(r.data.title,120),body:str(r.data.body,1000),createdAt:Date.now()});return {ok:true};});
exports.message=wrap(async r=>{
 const uid=user(r),ref=db.doc(`sessions/${id(r.data.id)}`),s=await ref.get();
 if(!s.exists||!s.data().members.includes(uid))fail("Not a session participant.","permission-denied");
 await ref.collection("messages").add({sender:uid,text:str(r.data.text,1000),createdAt:Date.now()});return {ok:true};
});

// One reusable provider order per monthly slot prevents cap races and abandoned-order overpurchase.
const ORDER_LOCK_MS=2*60000;
async function razor(path,body){
 const response=await fetch("https://api.razorpay.com/v1/"+path,{method:body?"POST":"GET",headers:{Authorization:"Basic "+Buffer.from(keyId.value()+":"+keySecret.value()).toString("base64"),"Content-Type":"application/json"},body:body?JSON.stringify(body):undefined});
 const result=await response.json();if(!response.ok)fail("Payment service unavailable. Try again later.");return result;
}
exports.createOrder=wrap(async r=>{
 const uid=user(r),month=new Date().toISOString().slice(0,7),quota=db.doc(`purchaseQuota/${uid}_${month}`);
 const slot=await db.runTransaction(async tx=>{
  const q=await tx.get(quota),d=q.data()||{count:0};if(d.count>=MONTHLY_LIMIT)fail("Monthly purchase limit reached.");
  const ref=db.doc(`paymentSlots/${uid}_${month}_${d.count}`),s=await tx.get(ref);
  if(s.exists){
   if(s.data().orderId)return {ref,data:s.data()};
   // A slot stuck in "creating" means an earlier provider call failed; after a short lock, one caller may recover it.
   if(Date.now()-s.data().createdAt<ORDER_LOCK_MS)fail("Order creation is in progress. Try again in a few minutes.");
   tx.update(ref,{createdAt:Date.now()});return {ref,data:null,recover:true};
  }
  tx.create(ref,{uid,month,status:"creating",createdAt:Date.now()});return {ref,data:null};
 });
 let orderId=slot.data?.orderId;
 if(!orderId){
  // The receipt is fixed per slot, so a recovering call adopts an order the provider already created instead of paying twice.
  const receipt=crypto.createHash("sha256").update(slot.ref.id).digest("hex").slice(0,40);
  if(slot.recover)orderId=(await razor("orders?receipt="+receipt)).items?.[0]?.id;
  if(!orderId)orderId=(await razor("orders",{amount:PRICE,currency:"INR",receipt,notes:{slot:slot.ref.id,uid}})).id;
  await db.runTransaction(async tx=>{tx.set(db.doc(`orders/${orderId}`),{uid,month,slot:slot.ref.id,amount:PRICE,credits:PACK,status:"pending",createdAt:Date.now()});tx.update(slot.ref,{orderId,status:"pending"});});
 }
 return {orderId,keyId:keyId.value(),amount:PRICE};
},{secrets:[keyId,keySecret]});
async function settle(orderId,paymentId,owner){
 const ref=db.doc(`orders/${id(orderId)}`),order=await ref.get();
 if(!order.exists || (owner && order.data().uid!==owner))fail("Order does not belong to this account.","permission-denied");
 const payment=await razor("payments/"+id(paymentId));
 if(payment.order_id!==orderId||payment.status!=="captured"||payment.amount!==PRICE||payment.currency!=="INR")fail("Payment is not captured yet. Retry recovery shortly.");
 await db.runTransaction(async tx=>{
  const o=await tx.get(ref),d=o.data();if(d.status==="credited")return;
  const quota=db.doc(`purchaseQuota/${d.uid}_${d.month}`),wr=wallet(d.uid);
  const [q,w]=await Promise.all([tx.get(quota),tx.get(wr)]);if(!w.exists)fail("Wallet missing.");
  const count=q.data()?.count||0;if(count>=MONTHLY_LIMIT)fail("Quota inconsistency. Contact administrator for a refund.");
  tx.update(wr,{balance:w.data().balance+PACK});tx.set(quota,{count:count+1});tx.update(ref,{status:"credited",paymentId});
  tx.update(db.doc(`paymentSlots/${d.slot}`),{status:"credited"});
  tx.set(db.doc("public/fund"),{paise:FieldValue.increment(PRICE/10)},{merge:true});
  ledger(tx,d.uid,orderId,"Purchased credit pack",PACK);
 });return {ok:true};
}
function signatureMatches(payload,signature,secret){
 if(typeof signature!=="string"||!/^[0-9a-f]{64}$/i.test(signature))return false;
 return crypto.timingSafeEqual(crypto.createHmac("sha256",secret).update(payload).digest(),Buffer.from(signature,"hex"));
}
exports.verifyPayment=wrap(async r=>{
 const uid=user(r),{orderId,paymentId,signature}=r.data;
 if(!signatureMatches(id(orderId)+"|"+id(paymentId),signature,keySecret.value()))fail("Invalid payment signature.","permission-denied");
 return settle(orderId,paymentId,uid);
},{secrets:[keyId,keySecret]});
exports.reconcilePayment=wrap(async r=>{
 const uid=user(r),orders=await db.collection("orders").where("uid","==",uid).get();
 for(const d of orders.docs.filter(x=>x.data().status==="pending")){
  const payments=await razor("orders/"+d.id+"/payments");
  const captured=payments.items?.find(p=>p.status==="captured");if(captured)await settle(d.id,captured.id,uid);
 }return {ok:true};
},{secrets:[keyId,keySecret]});
exports.paymentWebhook=onRequest({region,secrets:[keyId,keySecret,webhookSecret]},async(req,res)=>{
 if(req.method!=="POST"){res.sendStatus(405);return;}
 if(!signatureMatches(req.rawBody,req.get("x-razorpay-signature"),webhookSecret.value())){res.sendStatus(401);return;}
 try{if(req.body.event==="payment.captured"){const p=req.body.payload.payment.entity;await settle(p.order_id,p.id,null);}res.status(200).send("ok");}catch(e){console.error(e);res.status(500).send("retry");}
});
async function notify(uid,title,body){
 const s=await db.doc(`tokens/${uid}`).get(),token=s.data()?.token;if(!token)return;
 try{await getMessaging().send({token,notification:{title,body}});}catch(e){console.warn("FCM delivery failed",e.code);}
}
exports.sessionNotification=onDocumentWritten({region,document:"sessions/{id}"},async event=>{
 const after=event.data.after.data(),before=event.data.before.data();if(!after||before?.status===after.status)return;
 // The member who made the change already sees a local notification on their own device.
 await Promise.all(after.members.filter(uid=>uid!==after.updatedBy).map(uid=>notify(uid,"Kairos session update",`${after.topic}: ${after.status}`)));
});
exports.chatNotification=onDocumentCreated({region,document:"sessions/{id}/messages/{message}"},async event=>{
 const message=event.data.data(),s=await db.doc(`sessions/${event.params.id}`).get();
 if(s.exists)await Promise.all(s.data().members.filter(uid=>uid!==message.sender).map(uid=>notify(uid,"New Kairos message","Open Kairos to read your session message.")));
});
