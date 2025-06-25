import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.quicktalk.R
import com.example.quicktalk.User

class UserAdapter(
    private val users: List<User>,
    private val onItemClick: (User) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userInfo: TextView = itemView.findViewById(R.id.textViewUserInfo)
        val onlineStatus: View = itemView.findViewById(R.id.onlineStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.user_item, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]
        holder.userInfo.text = "${user.firstName} ${user.lastName}"

        // Устанавливаем цвет индикатора
        if (user.online) {
            holder.onlineStatus.setBackgroundResource(R.drawable.circle_green)
        } else {
            holder.onlineStatus.setBackgroundResource(R.drawable.circle_red)
        }

        holder.itemView.setOnClickListener {
            onItemClick(user)
        }
    }

    override fun getItemCount() = users.size
}