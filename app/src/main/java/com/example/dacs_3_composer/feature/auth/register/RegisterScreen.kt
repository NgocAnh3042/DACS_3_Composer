package com.example.dacs_3_composer.feature.auth.register

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dacs_3_composer.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    modifier: Modifier = Modifier,
    onRegisterClick: (String, String, String, String, String, String) -> Unit = { _, _, _, _, _, _ -> },
    onNavigateToLogin: () -> Unit = {}
) {
    var fullName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var agreeToTerms by remember { mutableStateOf(false) }

    // Role selection
    val roles = listOf("Khách hàng", "Nhà hàng", "Shipper")
    val roleKeys = listOf("user", "restaurant", "shipper")
    var selectedRoleIndex by remember { mutableStateOf(0) }
    var expanded by remember { mutableStateOf(false) }

    val primaryColor = Color(0xFF2159BC)
    val primaryContainer = Color(0xFF4173D7)
    val surfaceColor = Color(0xFFF8F9FA)
    val onSurfaceColor = Color(0xFF191C1D)
    val onSurfaceVariantColor = Color(0xFF414754)
    val outlineColor = Color(0xFF727785)
    val surfaceContainerHighest = Color(0xFFE1E3E4)
    val surfaceContainerLowest = Color(0xFFFFFFFF)
    val borderColor = Color(0xFFD1D5DB)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Thành viên mới",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateToLogin) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = primaryColor)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = surfaceColor.copy(alpha = 0.8f)
                )
            )
        },
        containerColor = surfaceColor
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Background Decorative Elements
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .offset(x = 200.dp, y = 500.dp)
                    .background(primaryColor.copy(alpha = 0.05f), RoundedCornerShape(120.dp))
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Hero Branding Section
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(surfaceContainerLowest, RoundedCornerShape(12.dp))
                        .border(1.dp, Color.Black.copy(alpha = 0.05f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.RestaurantMenu,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = primaryColor
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Tạo tài khoản mới",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = onSurfaceColor
                )
                Text(
                    text = "Bắt đầu hành trình khám phá ẩm thực cao cấp cùng AnChinGo",
                    fontSize = 14.sp,
                    color = onSurfaceVariantColor,
                    modifier = Modifier.padding(top = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Role Selection Field
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Bạn đăng ký với vai trò",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF414754),
                        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                    )
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = roles[selectedRoleIndex],
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .border(1.dp, borderColor, RoundedCornerShape(28.dp)),
                            leadingIcon = {
                                Icon(Icons.Default.Badge, contentDescription = null, tint = outlineColor)
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = primaryColor.copy(alpha = 0.5f),
                                unfocusedBorderColor = Color.Transparent,
                            ),
                            shape = RoundedCornerShape(28.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            roles.forEachIndexed { index, roleName ->
                                DropdownMenuItem(
                                    text = { Text(roleName) },
                                    onClick = {
                                        selectedRoleIndex = index
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Full Name
                RegisterInputField(
                    label = "Họ và tên",
                    value = fullName,
                    onValueChange = { fullName = it },
                    placeholder = "Nguyễn Văn A",
                    leadingIcon = Icons.Default.Person,
                    outlineColor = outlineColor,
                    containerColor = Color.White,
                    borderColor = borderColor
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Phone Number
                RegisterInputField(
                    label = "Số điện thoại",
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    placeholder = "090 123 4567",
                    leadingIcon = Icons.Default.Call,
                    outlineColor = outlineColor,
                    containerColor = Color.White,
                    borderColor = borderColor,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Email
                RegisterInputField(
                    label = "Email",
                    value = email,
                    onValueChange = { email = it },
                    placeholder = "example@gmail.com",
                    leadingIcon = Icons.Default.Mail,
                    outlineColor = outlineColor,
                    containerColor = Color.White,
                    borderColor = borderColor,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Password
                RegisterInputField(
                    label = "Mật khẩu",
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "••••••••",
                    leadingIcon = Icons.Default.Lock,
                    outlineColor = outlineColor,
                    containerColor = Color.White,
                    borderColor = borderColor,
                    isPassword = true,
                    passwordVisible = passwordVisible,
                    onPasswordToggle = { passwordVisible = !passwordVisible }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Confirm Password
                RegisterInputField(
                    label = "Nhập lại mật khẩu",
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    placeholder = "••••••••",
                    leadingIcon = Icons.Default.EnhancedEncryption,
                    outlineColor = outlineColor,
                    containerColor = Color.White,
                    borderColor = borderColor,
                    isPassword = true,
                    passwordVisible = false,
                    onPasswordToggle = {}
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Terms Checkbox
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Checkbox(
                        checked = agreeToTerms,
                        onCheckedChange = { agreeToTerms = it },
                        colors = CheckboxDefaults.colors(checkedColor = primaryColor)
                    )
                    Text(
                        text = "Tôi đồng ý với Điều khoản & Chính sách của ứng dụng.",
                        fontSize = 13.sp,
                        color = onSurfaceVariantColor,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .clickable { agreeToTerms = !agreeToTerms }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Register Button
                Button(
                    onClick = {
                        onRegisterClick(fullName, phoneNumber, email, password, confirmPassword, roleKeys[selectedRoleIndex])
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(primaryColor, primaryContainer)
                                ),
                                RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Đăng ký",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                // Social Register Divider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = outlineColor.copy(alpha = 0.2f))
                    Text(
                        text = "HOẶC ĐĂNG KÝ BẰNG",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = outlineColor,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        letterSpacing = 1.sp
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = outlineColor.copy(alpha = 0.2f))
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Social Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SocialButton(
                        text = "Google",
                        iconRes = R.drawable.ic_google_logo,
                        modifier = Modifier.weight(1f),
                        onClick = {}
                    )
                    SocialButton(
                        text = "Facebook",
                        iconRes = R.drawable.ic_facebook_logo,
                        modifier = Modifier.weight(1f),
                        onClick = {}
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))

                // Footer
                Row(
                    modifier = Modifier.padding(bottom = 32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Đã có tài khoản?",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = onSurfaceVariantColor
                    )
                    Text(
                        text = "Đăng nhập",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .clickable { onNavigateToLogin() }
                    )
                }
            }
        }
    }
}

@Composable
fun RegisterInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    outlineColor: Color,
    containerColor: Color,
    borderColor: Color,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onPasswordToggle: () -> Unit = {},
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF414754),
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, borderColor, RoundedCornerShape(28.dp)),
            placeholder = { Text(placeholder, color = outlineColor) },
            leadingIcon = {
                Icon(leadingIcon, contentDescription = null, tint = outlineColor)
            },
            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = onPasswordToggle) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = outlineColor
                        )
                    }
                }
            } else null,
            visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = keyboardOptions,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = containerColor,
                unfocusedContainerColor = containerColor,
                focusedBorderColor = Color(0xFF2159BC).copy(alpha = 0.5f),
                unfocusedBorderColor = Color.Transparent,
            ),
            shape = RoundedCornerShape(28.dp),
            singleLine = true
        )
    }
}

@Composable
fun SocialButton(
    text: String,
    iconRes: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(56.dp)
            .clickable { onClick() }
            .border(1.dp, Color(0xFFC1C6D6).copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = text,
                modifier = Modifier.size(20.dp),
                tint = Color.Unspecified
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RegisterScreenPreview() {
    RegisterScreen()
}
