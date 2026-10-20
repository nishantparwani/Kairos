"use strict";
const BONUS = 120, PACK = 120, PRICE = 10000, MONTHLY_LIMIT = 2;
function validateMinutes(n) { if (!Number.isInteger(n) || n < 15 || n > 180 || n % 15) throw Error("Duration must be 15–180 minutes in steps of 15."); return n; }
function transition(s, uid, action) {
 if (!s.members.includes(uid)) throw Error("Not a participant.");
 if (action === "accept") { if (uid !== s.mentor || s.status !== "requested") throw Error("Only the mentor can accept a new request."); return {...s,status:"accepted"}; }
 if (action === "cancel") { if (!(s.status === "requested" || (s.status === "accepted" && s.confirmations.length === 0))) throw Error("This session cannot be cancelled. Contact the administrator for a dispute."); return {...s,status:"cancelled"}; }
 if (action === "complete") {
  if (s.status === "completed" && s.confirmations.includes(uid)) return s;
  if (s.status !== "accepted") throw Error("Accept the session first.");
  const confirmations = [...new Set([...s.confirmations,uid])];
  return {...s,confirmations,status:confirmations.length===2?"completed":"accepted"};
 }
 throw Error("Invalid session action.");
}
module.exports={BONUS,PACK,PRICE,MONTHLY_LIMIT,validateMinutes,transition};
